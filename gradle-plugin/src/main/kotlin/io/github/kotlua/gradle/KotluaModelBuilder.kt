package io.github.kotlua.gradle

import io.github.kotlua.gradle.model.KotluaGradleModel
import io.github.kotlua.gradle.model.impl.DefaultKotlinLanguageSettings
import io.github.kotlua.gradle.model.impl.DefaultKotluaCompilation
import io.github.kotlua.gradle.model.impl.DefaultKotluaGradleModel
import io.github.kotlua.gradle.model.impl.DefaultKotluaSourceSet
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.tooling.provider.model.ToolingModelBuilder

class KotluaModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean {
        return modelName == KotluaGradleModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): Any {
        val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
            ?: return emptyModel()

        val luaMain = javaExtension.sourceSets.findByName("luaMain")
            ?: return emptyModel()

        val compileTask = project.tasks.withType(Kotlin2LuaCompile::class.java).findByName("kotlinLuaCompile")
            ?: return emptyModel()

        val klibs = runCatching {
            val config = project.configurations.findByName(luaMain.implementationConfigurationName)
                ?: project.configurations.findByName("compileClasspath")
            config?.incoming?.artifacts?.artifactFiles?.files
                ?.filter { it.extension == "klib" || it.isDirectory }
                ?.toSet() ?: emptySet()
        }.getOrElse { emptySet() }

        val compilerOptions = compileTask.compilerOptions
        val userArgs = compilerOptions.freeCompilerArgs.getOrElse(emptyList())

        val languageSettings = DefaultKotlinLanguageSettings(
            languageVersion = compilerOptions.languageVersion.orNull?.version,
            apiVersion = compilerOptions.apiVersion.orNull?.version,
            isProgressiveMode = compilerOptions.progressiveMode.getOrElse(false),
            optInAnnotationsInUse = compilerOptions.optIn.getOrElse(emptyList()).toSet(),
            freeCompilerArgs = emptyArray(),
        )

        val commonMain = javaExtension.sourceSets.findByName("commonMain")
        val commonKotlinDirs = (commonMain?.extensions?.findByName("kotlin") as? SourceDirectorySet)?.srcDirs ?: emptySet()

        val commonSourceSetModel = DefaultKotluaSourceSet(
            name = "commonMain",
            sourceDirs = commonKotlinDirs,
            resourceDirs = commonMain?.resources?.srcDirs ?: emptySet(),
            dependsOn = emptySet(),
            isTest = false,
            languageSettings = languageSettings
        )

        val luaKotlinDirs = (luaMain.extensions.findByName("kotlin") as? SourceDirectorySet)?.srcDirs ?: emptySet()

        val luaSourceSetModel = DefaultKotluaSourceSet(
            name = "luaMain",
            sourceDirs = luaKotlinDirs,
            resourceDirs = luaMain.resources.srcDirs,
            dependsOn = setOf(commonSourceSetModel.name),
            isTest = false,
            languageSettings = languageSettings
        )

        val destinationDir = compileTask.destinationDirectory.orNull?.asFile
            ?: project.layout.buildDirectory.dir("classes/kotlin/luaMain").get().asFile

        return DefaultKotluaGradleModel(
            kotluaPluginVersion = KotluaBuildConfig.VERSION,
            sourceSetsByName = mapOf(
                commonSourceSetModel.name to commonSourceSetModel,
                luaSourceSetModel.name to luaSourceSetModel
            ),
            compilationsBySourceSetName = mapOf(
                luaSourceSetModel.name to DefaultKotluaCompilation(
                    name = "main",
                    sourceSetName = luaMain.name,
                    outputDir = destinationDir,
                    compilerArguments = userArgs,
                    compileClasspath = klibs
                ),
                commonSourceSetModel.name to DefaultKotluaCompilation(
                    name = "main",
                    sourceSetName = commonSourceSetModel.name,
                    outputDir = project.layout.buildDirectory.dir("classes/kotlin/commonMain").get().asFile,
                    compilerArguments = userArgs,
                    compileClasspath = emptySet(),
                ),
            )
        )
    }

    private fun emptyModel(): KotluaGradleModel {
        return DefaultKotluaGradleModel(
            kotluaPluginVersion = KotluaBuildConfig.VERSION,
            sourceSetsByName = emptyMap(),
            compilationsBySourceSetName = emptyMap()
        )
    }
}
