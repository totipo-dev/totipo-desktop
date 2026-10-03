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
  centralCache = cacheData."https://repo.maven.apache.org/maven2/org" or { };
  # The dependency cache is stale after a repin until the operator regenerates it.
  # Match the actual locked modules so a future dependency update also fails closed.
  totipoLocks = builtins.filter (line: lib.hasPrefix "org.totipo:" line)
    (lib.splitString "\n" (builtins.readFile ./gradle.lockfile));
  cacheReady = builtins.length totipoLocks == 2 && builtins.all
    (line:
      let
        coordinate = lib.splitString ":" (builtins.head (lib.splitString "=" line));
        key = "totipo#${builtins.elemAt coordinate 1}/${builtins.elemAt coordinate 2}";
        entry = centralCache.${key} or { };
      in
      entry ? jar && (entry ? module || entry ? pom)
    )
    totipoLocks;
  # Only desktop sources, build inputs and packaged license notices.
  trees = [
    "src"
    "gradle"
    "packaging/licenses"
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
  ];
  gradleBuildTask = "installDist verifyDistributionArchives";
  doCheck = true;
  gradleCheckTask = "check";
  # Fetch exactly the tasks the package will execute, including desktop tests.
  gradleUpdateTask = "installDist verifyDistributionArchives check";
  preBuild = ''
    if [ -z "''${IN_GRADLE_UPDATE_DEPS:-}" ] && [ "${if cacheReady then "yes" else "no"}" != yes ]; then
      echo 'package-deps.json is stale or ungenerated; run mitmCache.updateScript as documented in README.md' >&2
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
    test -f "$out/lib/totipo-desktop/lib/totipo-desktop-${version}.jar"
    test -f "$out/lib/totipo-desktop/lib/totipo-storage-nio-0.1.1.jar"
    test -f "$out/lib/totipo-desktop/lib/totipo-core-0.1.1.jar"
    test -f "$out/lib/totipo-desktop/lib/bcprov-jdk18on-1.86.jar"
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
    test -f "$out/share/doc/totipo-desktop/licenses/TOTIPO_JAVA_LICENSE"
    test -z "$(find "$out" \( -name '.gradle' -o -name '.git' -o -name '*.java' -o -iname '*junit*.jar' -o -iname '*test*.jar' \) -print -quit)"
    runHook postInstallCheck
  '';
  meta = {
    description = "Swing desktop application for password-protected Totipo TOTP vaults";
    homepage = "https://github.com/totipo-org/totipo-desktop";
    license = lib.licenses.asl20;
    mainProgram = "totipo-desktop";
    platforms = lib.platforms.linux;
    sourceProvenance = with lib.sourceTypes; [ fromSource binaryBytecode ];
  };
})
