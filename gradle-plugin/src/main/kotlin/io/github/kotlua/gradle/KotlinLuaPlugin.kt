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
    private val toolingFactory: ToolingModelBuilderRegistry,
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("java-base")
        toolingFactory.register(KotluaModelBuilder())

        val extension = project.extensions.create<KotluaExtension>("kotlua")
        val javaExtension = project.extensions.getByType<JavaPluginExtension>()

        val commonMain = javaExtension.registerSourceSet("commonMain")
        val luaMain = javaExtension.registerSourceSet("luaMain")

        val compilerClasspath =
            project.configurations.create("kotluaCompilerClasspath") {
                isCanBeResolved = true
                isCanBeConsumed = false
                defaultDependencies {
                    add(
                        project.dependencies.create(
                            "${KotluaBuildConfig.GROUP}:${KotluaBuildConfig.COMPILER_ARTIFACT}:${KotluaBuildConfig.VERSION}",
                        ),
                    )
                }
            }

        val compileOutputDirectory = project.layout.buildDirectory.dir("lua/classes")
        val linkOutputDirectory = project.layout.buildDirectory.dir("lua/generated")

        extension.compilerOptions.moduleName.convention(project.name)

        val compileTask = project.tasks.register<Kotlin2LuaCompile>("kotlinLuaCompile", extension.compilerOptions)
        compileTask.configure {
            this.multiPlatformEnabled.set(extension.multiPlatformEnabled)
            this.destinationDirectory.set(compileOutputDirectory)

            this.compilerClasspath.from(compilerClasspath)

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

        val luaApplication = project.extensions.create<LuaApplicationExtension>("luaApplication")

        project.tasks.register<LuaRunTask>("luaRunApplication") {
            group = "application"
            description = "Runs the compiled Lua application"
            dependsOn(linkTask)
            outputDir.set(linkTask.flatMap { it.destinationDirectory })
            entryFile.convention(luaApplication.entryFile)
            programArgs.convention(luaApplication.args)

            binariesDirectories.from(project.layout.projectDirectory.dir("binaries"))
            val rootBinaries = project.layout.settingsDirectory.dir("binaries")
            if (rootBinaries.asFile != project.layout.projectDirectory.dir("binaries").asFile) {
                binariesDirectories.from(rootBinaries)
            }
        }

        project.tasks.named("assemble").configure {
            dependsOn(linkTask)
        }

        val withStdlib = project.providers
            .gradleProperty("kotlua.stdlib.default.dependency")
            .map { it.toBoolean() }
            .orElse(true)

        if (withStdlib.get()) {
            project.dependencies.add(
                luaMain.implementationConfigurationName,
                "${KotluaBuildConfig.GROUP}:${KotluaBuildConfig.STDLIB_ARTIFACT}:${KotluaBuildConfig.VERSION}",
            )
        }
    }

    private fun JavaPluginExtension.registerSourceSet(name: String) = sourceSets.create(name).apply {
        java.setSrcDirs(emptyList<Any>())
        resources.setSrcDirs(emptyList<Any>())
        val kotlinSourceSet = objects.sourceDirectorySet("kotlin", "Kotlin sources for $name")
        kotlinSourceSet.srcDir("src/$name/kotlin")
        kotlinSourceSet.filter.include("**/*.kt", "**/*.kts")

        (this as ExtensionAware).extensions.add(
            SourceDirectorySet::class.java,
            "kotlin",
            kotlinSourceSet,
        )
        allSource.source(kotlinSourceSet)
    }
}
