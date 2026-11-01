package io.github.kotlua.gradle

import io.github.kotlua.cli.compiler.K2LuaCompilerArguments
import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptions
import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptionsDefault
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.process.ExecOperations
import org.gradle.work.NormalizeLineEndings
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.ContributeCompilerArgumentsContext
import javax.inject.Inject

@OptIn(InternalKotlinGradlePluginApi::class)
abstract class KotlinLuaIrLink @Inject constructor(
    objectFactory: ObjectFactory,
    execOperations: ExecOperations,
) : Kotlin2LuaCompile(
    compilerOptions = objectFactory.newInstance<KotlinLuaCompilerOptionsDefault>(),
    objectFactory = objectFactory,
    execOperations = execOperations
) {
    init {
        disallowSourceChanges()
    }

    @get:Internal
    override val sources: FileCollection = super.sources

    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    internal abstract val entryModule: DirectoryProperty

    override fun contributeAdditionalCompilerArguments(context: ContributeCompilerArgumentsContext<K2LuaCompilerArguments>) {
        context.primitive { args ->
            args.includes = entryModule.get().asFile.absolutePath
        }
    }
}
