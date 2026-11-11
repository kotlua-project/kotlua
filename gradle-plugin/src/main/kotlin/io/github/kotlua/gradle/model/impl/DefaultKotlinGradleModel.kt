package io.github.kotlua.gradle.model.impl

import io.github.kotlua.gradle.model.KotlinLanguageSettings
import io.github.kotlua.gradle.model.KotluaCompilation
import io.github.kotlua.gradle.model.KotluaGradleModel
import io.github.kotlua.gradle.model.KotluaSourceSet
import java.io.File

data class DefaultKotluaSourceSet(
    override val name: String,
    override val sourceDirs: Set<File>,
    override val resourceDirs: Set<File> = emptySet(),
    override val dependsOn: Set<String> = emptySet(),
    override val additionalVisibleSourceSets: Set<String> = emptySet(),
    override val isTest: Boolean = false,
    override val languageSettings: KotlinLanguageSettings = DefaultKotlinLanguageSettings()
) : KotluaSourceSet

data class DefaultKotluaCompilation(
    override val name: String,
    override val sourceSetName: String,
    override val outputDir: File,
    override val compilerArguments: List<String>,
    override val compileClasspath: Set<File>
) : KotluaCompilation

data class DefaultKotluaGradleModel(
    override val kotluaPluginVersion: String,
    override val sourceSetsByName: Map<String, KotluaSourceSet>,
    override val compilationsBySourceSetName: Map<String, KotluaCompilation>
) : KotluaGradleModel

data class DefaultKotlinLanguageSettings(
    override val languageVersion: String? = "",
    override val apiVersion: String? = "",
    override val isProgressiveMode: Boolean = false,
    override val enabledLanguageFeatures: Set<String> = emptySet(),
    override val optInAnnotationsInUse: Set<String> = emptySet(),
    override val compilerPluginArguments: Array<String> = emptyArray(),
    override val compilerPluginClasspath: Set<File> = emptySet(),
    override val freeCompilerArgs: Array<String> = emptyArray()
) : KotlinLanguageSettings
