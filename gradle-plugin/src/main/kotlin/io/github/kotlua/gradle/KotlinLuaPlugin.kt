package io.github.kotlua.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

class KotlinLuaPlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val toolingFactory: ToolingModelBuilderRegistry
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("java-base")
        toolingFactory.register(KotluaModelBuilder())

        val extension = project.extensions.create<KotluaExtension>("kotlua")
        val javaExtension = project.extensions.getByType<JavaPluginExtension>()

        fun registerSourceSet(name: String) = javaExtension.sourceSets.create(name).apply {
            java.setSrcDirs(emptyList<Any>())
            resources.setSrcDirs(emptyList<Any>())
            val kotlinSourceSet = objects.sourceDirectorySet("kotlin", "Kotlin sources for $name")
            kotlinSourceSet.srcDir("src/$name/kotlin")
            kotlinSourceSet.filter.include("**/*.kt", "**/*.kts")

            (this as ExtensionAware).extensions.add(
                SourceDirectorySet::class.java,
                "kotlin",
                kotlinSourceSet
            )
            allSource.source(kotlinSourceSet)
        }

        val commonMain = registerSourceSet("commonMain")
        val luaMain = registerSourceSet("luaMain")

        val compilerClasspath = project.configurations.create("kotluaCompilerClasspath") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        val compileOutputDirectory = project.layout.buildDirectory.dir("lua/classes")
        val linkOutputDirectory = project.layout.buildDirectory.dir("lua/generated")

        val compileTask = project.tasks.register<Kotlin2LuaCompile>("kotlinLuaCompile", extension.compilerOptions)
        compileTask.configure {
            this.multiPlatformEnabled.set(extension.multiPlatformEnabled)
            this.destinationDirectory.set(compileOutputDirectory)

            this.compilerClasspath.from(
                extension.compilerClasspath.let { if (it.isEmpty) compilerClasspath else it }
            )

            this.commonSourceSet.from(commonMain.kotlin.sourceDirectories)
            this.source(luaMain.kotlin.sourceDirectories)
            this.libraries.from(luaMain.compileClasspath)
        }

        val linkTask = project.tasks.register<KotlinLuaIrLink>("kotlinLuaIrLink")
        linkTask.configure {
            this.compilerOptions.moduleName.set(extension.compilerOptions.moduleName)
            this.multiPlatformEnabled.set(extension.multiPlatformEnabled)

            this.entryModule = compileTask.map { it.destinationDirectory.asFile.orNull }
            this.compilerClasspath.from(compileTask.map { it.compilerClasspath })
            this.destinationDirectory.set(linkOutputDirectory)
            this.libraries.from(luaMain.compileClasspath)
        }

        project.tasks.named("assemble").configure {
            dependsOn(linkTask)
        }
    }
}
