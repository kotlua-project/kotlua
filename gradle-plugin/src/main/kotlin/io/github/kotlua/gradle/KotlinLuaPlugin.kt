package io.github.kotlua.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class KotlinLuaPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create<KotluaExtension>("kotlua")
        val compilerClasspath = target.configurations.create("kotluaCompilerClasspath") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        val compileTask = target.tasks.register<Kotlin2LuaCompile>("kotlinLuaCompile", extension.compilerOptions)
        val compileOutputDirectory = target.layout.buildDirectory.dir("lua").map { it.dir("classes") }
        val linkOutputDirectory = target.layout.buildDirectory.dir("lua").map { it.dir("generated") }

        compileTask.configure {
            this.multiPlatformEnabled.set(extension.multiPlatformEnabled.get())
            this.destinationDirectory.set(compileOutputDirectory.get())
            this.compilerClasspath.from(extension.compilerClasspath.takeIf { !it.isEmpty } ?: compilerClasspath)
            this.source(target.fileTree("src/lua/kotlin"))
        }

        val linkTask = target.tasks.register<KotlinLuaIrLink>("kotlinLuaIrLink")
        linkTask.configure {
            dependsOn(compileTask)
            this.compilerOptions.moduleName.set(extension.compilerOptions.moduleName.get())
            this.multiPlatformEnabled.set(extension.multiPlatformEnabled.get())
            this.entryModule.set(compileTask.get().destinationDirectory.asFile.get())
            this.compilerClasspath.from(compileTask.get().compilerClasspath)
            this.destinationDirectory.set(linkOutputDirectory.get())
        }

        target.tasks.maybeCreate("assemble").dependsOn(linkTask)
    }

}
