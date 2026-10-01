{ lib
, stdenv
, jdk
, gradle
, makeWrapper
, makeDesktopItem
, copyDesktopItems
, coreutils
, findutils
, gnused
}:
let
  rawVersion = builtins.readFile ./VERSION;
  version = if lib.hasSuffix "\r\n" rawVersion then lib.removeSuffix "\r\n" rawVersion else lib.removeSuffix "\n" rawVersion;
  cacheData = builtins.fromJSON (builtins.readFile ./package-deps.json);
  cacheReady = builtins.length (builtins.attrNames (builtins.removeAttrs cacheData [ "!comment" "!version" ])) > 0;
  # Explicit trees, including checked-out submodule contents, not a Git fileset.
  trees = [
    "src"
    "gradle"
    "packaging/licenses"
    "vendor/totipo-java/gradle"
    "vendor/totipo-java/core/src"
    "vendor/totipo-java/storage-nio/src"
    "vendor/totipo-java/SPEC_PIN.md"
  ];
  files = [
    "build.gradle.kts"
    "settings.gradle.kts"
    "gradle.properties"
    "gradle.lockfile"
    "settings-gradle.lockfile"
    "VERSION"
    "LICENSE"
    "THIRD_PARTY.md"
    "packaging/strict-locking.gradle"
    "vendor/totipo-java/build.gradle.kts"
    "vendor/totipo-java/settings.gradle.kts"
    "vendor/totipo-java/gradle.properties"
    "vendor/totipo-java/settings-gradle.lockfile"
    "vendor/totipo-java/LICENSE"
    "vendor/totipo-java/core/build.gradle.kts"
    "vendor/totipo-java/core/gradle.lockfile"
    "vendor/totipo-java/storage-nio/build.gradle.kts"
    "vendor/totipo-java/storage-nio/gradle.lockfile"
  ];
in
assert builtins.match "[A-Za-z0-9][A-Za-z0-9._+-]*" version != null;
stdenv.mkDerivation (finalAttrs: {
  pname = "totipo-desktop";
  inherit version;
  src = lib.cleanSourceWith {
    src = ./.;
    filter = path: type:
      let
        rel = lib.removePrefix (toString ./. + "/") (toString path);
        parts = lib.splitString "/" rel;
        excluded = builtins.any (p: builtins.elem p [ ".git" ".gradle" "build" ".idea" ".vscode" ".direnv" "review" ]) parts
          || builtins.any (suffix: lib.hasSuffix suffix rel) [ "~" ".tmp" ".swp" ".iml" ];
        selected = builtins.elem rel files
          || builtins.any (tree: rel == tree || lib.hasPrefix (tree + "/") rel) trees
          || (type == "directory" && builtins.any (entry: lib.hasPrefix (rel + "/") entry) (trees ++ files));
      in
      !excluded && type != "symlink" && selected;
  };
  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./package-deps.json;
  };
  nativeBuildInputs = [ gradle makeWrapper copyDesktopItems ];
  buildInputs = [ jdk ];
  JAVA_HOME = jdk;
  # NIO's filename encoding follows the native locale, not -Dfile.encoding.
  # Use glibc's built-in UTF-8 locale for both fetchDeps and package tests.
  LC_ALL = "C.UTF-8";
  gradleFlags = [
    "--no-configuration-cache"
    "--dependency-verification=strict"
    "--init-script"
    "packaging/strict-locking.gradle"
  ];
  gradleBuildTask = "installDist verifyDistributionArchives";
  doCheck = true;
  gradleCheckTask = "check :totipo-java:check";
  # Fetch exactly the tasks the package will execute, including the composite tests.
  gradleUpdateTask = "installDist verifyDistributionArchives check :totipo-java:check";
  preBuild = ''
    test -f vendor/totipo-java/core/src/main/java/dev/totipo/VaultState.java
    test -f vendor/totipo-java/storage-nio/src/main/java/dev/totipo/storage/nio/NioTotipo.java
    test -f vendor/totipo-java/core/src/test/resources/totipo-spec/v1-pre-rc/spec/totipo-vault-format-v1.md
    if [ -z "''${IN_GRADLE_UPDATE_DEPS:-}" ] && [ "${if cacheReady then "yes" else "no"}" != yes ]; then
      echo 'package-deps.json is ungenerated; run mitmCache.updateScript as documented in README.md' >&2
      exit 1
    fi
  '';
  installPhase = ''
    runHook preInstall
    mkdir -p "$out/bin" "$out/lib/totipo-desktop" "$out/share/doc/totipo-desktop"
    cp -r build/install/totipo-desktop/{bin,lib} "$out/lib/totipo-desktop/"
    mv "$out/lib/totipo-desktop/bin/totipo-desktop" "$out/lib/totipo-desktop/bin/totipo-desktop-unwrapped"
    rm "$out/lib/totipo-desktop/bin/totipo-desktop.bat"
    cp -r build/install/totipo-desktop/{LICENSE,THIRD_PARTY.md,VERSION,licenses} "$out/share/doc/totipo-desktop/"
    makeWrapper "$out/lib/totipo-desktop/bin/totipo-desktop-unwrapped" "$out/bin/totipo-desktop" \
      --set JAVA_HOME ${jdk} \
      --prefix PATH : ${lib.makeBinPath [ coreutils findutils gnused ]}
    runHook postInstall
  '';
  desktopItems = [
    (makeDesktopItem {
      name = "totipo-desktop";
      desktopName = "Totipo";
      exec = "totipo-desktop";
      categories = [ "Utility" ];
      terminal = false;
      comment = "Password-protected Totipo TOTP vaults";
    })
  ];
  doInstallCheck = true;
  installCheckPhase = ''
    runHook preInstallCheck
    test -x "$out/bin/totipo-desktop"
    grep -F -- '${jdk}' "$out/bin/totipo-desktop"
    grep -F -- '-XX:+DisableAttachMechanism' "$out/lib/totipo-desktop/bin/totipo-desktop-unwrapped"
    test "$(find "$out/lib/totipo-desktop/lib" -type f -name '*.jar' | wc -l)" -eq 4
    for jar in build/install/totipo-desktop/lib/*.jar; do
      cmp "$jar" "$out/lib/totipo-desktop/lib/$(basename "$jar")"
    done
    desktop="$out/share/applications/totipo-desktop.desktop"
    grep -x 'Name=Totipo' "$desktop"
    grep -x 'Exec=totipo-desktop' "$desktop"
    # makeDesktopItem joins lists without a trailing separator; both forms are valid.
    grep -Ex 'Categories=Utility;?' "$desktop"
    grep -x 'Terminal=false' "$desktop"
    test -f "$out/share/doc/totipo-desktop/LICENSE"
    test -f "$out/share/doc/totipo-desktop/licenses/BOUNCY_CASTLE_LICENSE.html"
    test -z "$(find "$out" \( -name '.gradle' -o -name '.git' -o -name '*.java' -o -iname '*junit*.jar' -o -iname '*test*.jar' \) -print -quit)"
    runHook postInstallCheck
  '';
  meta = {
    description = "Swing desktop application for password-protected Totipo TOTP vaults";
    homepage = "https://github.com/totipo-dev/totipo-desktop";
    license = lib.licenses.asl20;
    mainProgram = "totipo-desktop";
    platforms = lib.platforms.linux;
    sourceProvenance = with lib.sourceTypes; [ fromSource binaryBytecode ];
  };
})
