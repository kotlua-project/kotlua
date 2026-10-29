plugins {
    id("org.jetbrains.kotlin.jvm") version "2.5.0-Beta2"
}

repositories {
    mavenCentral()
}

val copyrightDirectory = project.layout.buildDirectory.dir("copyright")
val mainProjectRootDir: File = gradle.parent?.rootProject?.rootDir ?: rootDir

dependencies {
    api("org.jetbrains.kotlin:kotlin-reflect:2.5.0-Beta2")
}

val copyCopyrightProfile by tasks.registering(Copy::class) {
    from("$rootDir/license")
    into(copyrightDirectory)
    include("apache.xml")
}

tasks {
    compileKotlin {
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-version", "-Xdont-warn-on-error-suppression"))
        }
    }

    register<JavaExec>("run") {
        group = "application"
        mainClass = "generators.GenerateStandardLibKt"
        classpath = sourceSets.main.get().runtimeClasspath
        args = listOf("$mainProjectRootDir")
        systemProperty("line.separator", "\n")
    }

    register<JavaExec>("generateStdlibTests") {
        group = "application"
        mainClass = "generators.GenerateStandardLibTestsKt"
        classpath = sourceSets.main.get().runtimeClasspath
        workingDir = mainProjectRootDir
        systemProperty("line.separator", "\n")
    }

    register<JavaExec>("generateUnicodeData") {
        group = "application"
        mainClass = "generators.unicode.GenerateUnicodeDataKt"
        classpath = sourceSets.main.get().runtimeClasspath
        args = listOf("$mainProjectRootDir")
    }

    register<JavaExec>("generateBuiltins") {
        group = "application"
        mainClass = "org.jetbrains.kotlin.generators.builtins.generateBuiltIns.GenerateBuiltInsKt"
        classpath = sourceSets.main.get().runtimeClasspath
        args = listOf("$mainProjectRootDir")
    }
}

sourceSets {
    main {
        kotlin.srcDir("src")
        resources.srcDir(copyCopyrightProfile)
    }
}
