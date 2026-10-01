import java.io.DataInputStream

plugins { application }

group = "dev.totipo"
description = "Totipo Swing desktop shell"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
application { mainClass.set("dev.totipo.desktop.TotipoDesktop") }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
tasks.compileJava { options.release.set(17) }
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

dependencies {
    implementation("dev.totipo:storage-nio")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

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
