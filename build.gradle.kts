import java.io.DataInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

plugins { application }

group = "org.totipo"
description = "Totipo Swing desktop shell"

// No terminator or one conventional newline; no other whitespace is accepted.
val versionText = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.get()
val applicationVersion = if (versionText.endsWith("\r\n")) versionText.dropLast(2) else versionText.removeSuffix("\n")
check(applicationVersion.isNotEmpty() && applicationVersion.none { it.isWhitespace() }) {
    "VERSION must contain one nonempty value without whitespace (one final newline is allowed)"
}
check(applicationVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*"))) { "Unsafe VERSION filename characters" }
version = applicationVersion.trim()
val releaseBuild = providers.gradleProperty("releaseBuild").map { it.toBooleanStrict() }.orElse(false)
check(!releaseBuild.get() || applicationVersion != "0.0.0-dev") {
    "A release build requires replacing the development VERSION"
}
tasks.register("validateVersion") {
    group = "verification"
    description = "Validate VERSION; -PreleaseBuild=true rejects the development sentinel"
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
application { mainClass.set("org.totipo.desktop.TotipoDesktop") }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
tasks.compileJava { options.release.set(17) }
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

val totipoJavaVersion = "0.1.1"
dependencies {
    implementation("org.totipo:totipo-storage-nio:$totipoJavaVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

val verifyMavenBoundary = tasks.register("verifyMavenBoundary") {
    group = "verification"
    description = "Require the exact external Totipo Maven dependency boundary"
    val expectedVersion = totipoJavaVersion
    val graphs = listOf(configurations.compileClasspath, configurations.runtimeClasspath).map { configuration ->
        configuration.get().incoming.resolutionResult.rootComponent
    }
    inputs.files(configurations.compileClasspath, configurations.runtimeClasspath)
    doLast {
        graphs.forEachIndexed { index, graph ->
            val root = graph.get()
            val visited = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
            fun visit(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                if (!visited.add(component)) return
                component.dependencies.forEach { dependency ->
                    check(dependency is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                        "Unresolved dependency: $dependency"
                    }
                    visit(dependency.selected)
                }
            }
            visit(root)
            val dependencies = visited.filter { it != root }
            // This single-project application has no legitimate project dependency.
            check(dependencies.none { it.id is ProjectComponentIdentifier }) { "Project/source dependency found" }
            val modules = dependencies.map { component ->
                check(component.id is ModuleComponentIdentifier) { "Non-Maven component: ${component.id}" }
                component.id as ModuleComponentIdentifier
            }
            val obsoleteGroup = listOf("dev", "totipo").joinToString(".")
            check(modules.none { it.group == obsoleteGroup || it.group.startsWith("$obsoleteGroup.") }) {
                "Obsolete Totipo Maven namespace found"
            }
            val totipo = modules.filter { it.group == "org.totipo" || it.group.startsWith("org.totipo.") }
            check(totipo.size == 2 && totipo.map { "${it.group}:${it.module}:${it.version}" }.toSet() == setOf(
                "org.totipo:totipo-storage-nio:$expectedVersion", "org.totipo:totipo-core:$expectedVersion"
            )) { "Unexpected or version-skewed Totipo modules: $totipo" }
            val direct = root.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .filter { !it.isConstraint }.map { it.selected.id }.filterIsInstance<ModuleComponentIdentifier>()
                .filter { it.group == "org.totipo" }
            check(direct.size == 1 && direct.single().module == "totipo-storage-nio") {
                "Only storage-nio may be a direct Totipo dependency: $direct"
            }
            val storage = dependencies.single { (it.id as? ModuleComponentIdentifier)?.module == "totipo-storage-nio" }
            check(storage.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .any { !it.isConstraint && (it.selected.id as? ModuleComponentIdentifier)?.let { id ->
                    id.group == "org.totipo" && id.module == "totipo-core" && id.version == expectedVersion
                } == true }) { "storage-nio must expose core transitively" }
            if (index == 1) {
                check(modules.count { it.group == "org.bouncycastle" && it.module == "bcprov-jdk18on" && it.version == "1.86" } == 1) {
                    "Expected BC 1.86 runtime dependency"
                }
            }
        }
        logger.lifecycle("Verified external Totipo Maven compile/runtime boundary")
    }
}
tasks.check { dependsOn(verifyMavenBoundary) }

val mainClasses = sourceSets.main.map { it.output.classesDirs }
val verifyJava17Bytecode = tasks.register("verifyJava17Bytecode") {
    group = "verification"
    description = "Verify every desktop production class is Java 17 bytecode"
    dependsOn(tasks.compileJava)
    inputs.files(mainClasses)
    doLast {
        val classes = inputs.files.asFileTree.matching { include("**/*.class") }.files
        check(classes.isNotEmpty()) { "No desktop classes to verify" }
        classes.forEach { file ->
            DataInputStream(file.inputStream()).use { input ->
                check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid class: $file" }
                check(input.readUnsignedShort() == 0) { "Preview bytecode: $file" }
                check(input.readUnsignedShort() == 61) { "Not Java 17 bytecode: $file" }
            }
        }
    }
}
tasks.check { dependsOn(verifyJava17Bytecode) }
tasks.test { dependsOn(verifyJava17Bytecode) }

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.8.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"
}

// Start scripts only; `run` retains normal debugger/Attach API availability.
tasks.startScripts { defaultJvmOpts = listOf("-XX:+DisableAttachMechanism") }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
distributions.main {
    contents {
        from("LICENSE", "THIRD_PARTY.md", "VERSION")
        from("packaging/licenses") { into("licenses") }
    }
}

val distributionDirectory = layout.buildDirectory.dir("install/totipo-desktop")
val runtimeFiles = configurations.runtimeClasspath
val desktopJar = tasks.jar.flatMap { it.archiveFile }
val buildHostPath = layout.projectDirectory.asFile.absolutePath
val verifyDistribution = tasks.register("verifyDistribution") {
    group = "verification"
    description = "Verify installDist runtime allowlist, notices and launcher security"
    dependsOn(tasks.installDist)
    inputs.dir(distributionDirectory)
    inputs.files(runtimeFiles, desktopJar)
    inputs.property("buildHostPath", buildHostPath)
    val rootDirectory = distributionDirectory
    val runtimeArtifacts = runtimeFiles.map { it.toList() }
    val applicationJar = desktopJar
    val hostPath = buildHostPath
    val dependencyVersion = totipoJavaVersion
    doLast {
        val root = rootDirectory.get().asFile
        val expectedArtifacts = runtimeArtifacts.get() + applicationJar.get().asFile
        val expectedJars = expectedArtifacts.map { it.name }.toSet()
        val patterns = listOf(
            Regex("totipo-desktop-[A-Za-z0-9._+-]+\\.jar"),
            Regex("totipo-storage-nio-${Regex.escape(dependencyVersion)}\\.jar"),
            Regex("totipo-core-${Regex.escape(dependencyVersion)}\\.jar"),
            Regex("bcprov-jdk18on-1\\.86\\.jar")
        )
        check(expectedJars.size == 4 && patterns.all { p -> expectedJars.count { p.matches(it) } == 1 }) {
            "Unexpected runtime composition: $expectedJars"
        }
        val notices = setOf("LICENSE", "THIRD_PARTY.md", "VERSION", "licenses/BOUNCY_CASTLE_LICENSE.html", "licenses/TOTIPO_JAVA_LICENSE")
        val expected = expectedJars.map { "lib/$it" }.toSet() + notices + setOf("bin/totipo-desktop", "bin/totipo-desktop.bat")
        val actual = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        check(actual == expected) { "Distribution inventory mismatch: missing ${expected - actual}; extra ${actual - expected}" }
        expectedArtifacts.forEach { artifact ->
            val digest = MessageDigest.getInstance("SHA-256")
            check(digest.digest(artifact.readBytes()).contentEquals(digest.digest(root.resolve("lib/${artifact.name}").readBytes()))) {
                "Packaged JAR differs from resolved build artifact: ${artifact.name}"
            }
        }
        check(root.resolve("bin/totipo-desktop").canExecute() || System.getProperty("os.name").startsWith("Windows")) {
            "Unix launcher is not executable"
        }
        listOf("bin/totipo-desktop", "bin/totipo-desktop.bat").forEach { name ->
            val script = root.resolve(name).readText()
            check("-XX:+DisableAttachMechanism" in script) { "Missing packaged hardening: $name" }
            check("org.totipo.desktop.TotipoDesktop" in script) { "Wrong application main class: $name" }
            check(hostPath !in script && !Regex("/home/|/Users/|/nix/store/|[A-Za-z]:[\\\\/]Users[\\\\/]").containsMatchIn(script)) {
                "Build-host path in $name"
            }
            val classpath = script.lineSequence().single { it.startsWith("CLASSPATH=") || it.startsWith("set CLASSPATH=") }
            val entries = classpath.substringAfter('=').split(if (name.endsWith(".bat")) ';' else ':')
            val prefix = if (name.endsWith(".bat")) "%APP_HOME%\\lib\\" else "\$APP_HOME/lib/"
            check(entries.toSet() == expectedJars.map { prefix + it }.toSet()) { "Unexpected launcher classpath: $name" }
        }
        expectedJars.forEach { name ->
            ZipFile(root.resolve("lib/$name")).use { jar ->
                val entries = jar.entries().asSequence().toList()
                check(entries.none { it.name.endsWith(".java") || it.name.startsWith("vendor/") || it.name.startsWith(".gradle/") }) {
                    "Source/cache embedded in $name"
                }
                if (name.startsWith("totipo-")) {
                    val classes = entries.filter { it.name.endsWith(".class") }
                    check(classes.isNotEmpty()) { "No production classes in $name" }
                    classes.forEach { entry ->
                        DataInputStream(jar.getInputStream(entry)).use { input ->
                            check(input.readInt() == 0xCAFEBABE.toInt() && input.readUnsignedShort() == 0 && input.readUnsignedShort() == 61) {
                                "Non-Java-17 production class: $name/${entry.name}"
                            }
                        }
                    }
                }
            }
        }
        logger.lifecycle("Verified distribution: {}", expectedJars.sorted())
    }
}
val zipContents = zipTree(tasks.distZip.flatMap { it.archiveFile })
val tarContents = tarTree(tasks.distTar.flatMap { it.archiveFile })
tasks.register("verifyDistributionArchives") {
    group = "verification"
    description = "Compare every ZIP/TAR file with verified installDist"
    dependsOn(verifyDistribution, tasks.distZip, tasks.distTar)
    inputs.files(zipContents, tarContents)
    inputs.dir(distributionDirectory)
    val rootDirectory = distributionDirectory
    val archiveTrees = listOf(zipContents, tarContents)
    doLast {
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
        val root = rootDirectory.get().asFile
        val expected = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to digest(it) }
        archiveTrees.forEach { tree ->
            val actual = mutableMapOf<String, List<Byte>>()
            tree.visit {
                if (!isDirectory) {
                    val path = relativePath.segments.drop(1).joinToString("/")
                    check(actual.put(path, digest(file)) == null) { "Duplicate archive entry: $path" }
                }
            }
            check(actual == expected) { "Archive does not match verified installDist" }
        }
    }
}
tasks.check { dependsOn(verifyDistribution) }

// Compile the separate harness in check; execution remains explicit and it is not distributed.
val qualification = sourceSets.create("qualification")
qualification.compileClasspath = sourceSets.main.get().compileClasspath
qualification.runtimeClasspath = qualification.output + sourceSets.main.get().runtimeClasspath
tasks.named<JavaCompile>(qualification.compileJavaTaskName) {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}
tasks.check { dependsOn(tasks.named(qualification.classesTaskName)) }
val qualificationRoot = providers.gradleProperty("qualificationRoot")
tasks.register<JavaExec>("filesystemQualification") {
    group = "verification"
    description = "Real-NIO qualification beneath an explicit -PqualificationRoot"
    classpath = qualification.runtimeClasspath
    mainClass.set("org.totipo.qualification.FilesystemQualification")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    val root = qualificationRoot
    argumentProviders.add(CommandLineArgumentProvider {
        check(root.isPresent) { "Supply -PqualificationRoot=/explicit/disposable/test/root" }
        listOf(root.get())
    })
}
