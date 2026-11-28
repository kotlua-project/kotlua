@file:Suppress("DEPRECATION")

package io.github.kotlua.gradle

import groovy.lang.Closure
import io.github.kotlua.cli.compiler.K2LuaCompilerArguments
import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptions
import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptionsHelper
import io.github.kotlua.gradle.worker.KotlinLuaCompileWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.*
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.Incremental
import org.gradle.work.NormalizeLineEndings
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.ContributeCompilerArgumentsContext
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.CreateCompilerArgumentsContext
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.CreateCompilerArgumentsContext.Companion.create
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File
import javax.inject.Inject

@OptIn(InternalKotlinGradlePluginApi::class)
abstract class Kotlin2LuaCompile @Inject constructor(
    @get:Nested val compilerOptions: KotlinLuaCompilerOptions,
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    providers: ProviderFactory,
) : DefaultTask(), KotlinCompileTool, KotlinCompilerArgumentsProducer, ProducesKlib {

    @Internal
    protected val sourceFileFilter = PatternSet()

    init {
        sourceFileFilter.include(
            setOf("kt", "kts").flatMap { ext -> ext.fileExtensionCasePermutations().map { "**/*.$it" } }
        )
        multiPlatformEnabled.convention(false)
    }

    @get:Classpath
    val compilerClasspath: ConfigurableFileCollection =
        objectFactory.fileCollection()

    @get:Input
    abstract val multiPlatformEnabled: Property<Boolean>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:Incremental
    @get:NormalizeLineEndings
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val commonSourceSet: ConfigurableFileCollection = objectFactory.fileCollection()

    override val klibOutput: Provider<File>
        get() = destinationDirectory.asFile

    @get:Internal
    val workerTmpDir: Property<String> =
        objectFactory.property(String::class.java)
            .convention(providers.systemProperty("java.io.tmpdir"))

    @TaskAction
    fun compile() {
        val args = createCompilerArguments(CreateCompilerArgumentsContext.default)
        val dependencies = libraries
            .map { it.normalize().absolutePath }

        args.libraries = dependencies.distinct().let {
            if (it.isNotEmpty())
                it.joinToString(File.pathSeparator)
            else
                null
        }

        val argsList = ArgumentUtils.convertArgumentsToStringList(args)
        val tmpDir = workerTmpDir.orNull?.takeIf { it.isNotBlank() }

        val workQueue = workerExecutor.processIsolation {
            classpath.from(compilerClasspath)
            forkOptions {
                maxHeapSize = "1g"
                tmpDir?.let {
                    systemProperty("java.io.tmpdir", tmpDir)
                    environment("TEMP", tmpDir)
                    environment("TMP", tmpDir)
                }
            }
        }
        workQueue.submit(KotlinLuaCompileWorkAction::class.java) {
            compilerArgs.set(argsList)
        }
    }

    protected open fun contributeAdditionalCompilerArguments(context: ContributeCompilerArgumentsContext<K2LuaCompilerArguments>) {
    }

    override fun createCompilerArguments(context: CreateCompilerArgumentsContext) = context.create<K2LuaCompilerArguments> {
        primitive { args ->
            args.multiPlatform = multiPlatformEnabled.get()

            KotlinLuaCompilerOptionsHelper.fillCompilerArguments(compilerOptions, args)
            args.outputDir = destinationDirectory.get().asFile.normalize().absolutePath
            args.moduleName = compilerOptions.moduleName.get()
        }

        dependencyClasspath { args ->
            args.libraries = runSafe {
                libraries
                    .map { it.absolutePath }
                    .toSet()
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(File.pathSeparator)
            }
        }

        sources { args ->
            if (multiPlatformEnabled.get()) {
                val commonSources = commonSourceSet.asFileTree.matching(sourceFileFilter).map { it.normalize().absolutePath }
                args.commonSources = commonSources.toTypedArray()
                args.freeArgs += commonSources
            }
            args.freeArgs += sources.asFileTree.files.map { it.absolutePath }
        }

        contributeAdditionalCompilerArguments(this)
    }

    @get:Internal
    internal val sourceFiles = objectFactory.fileCollection()

    override val sources: FileCollection = objectFactory.fileCollection()
        .from(
            { sourceFiles.asFileTree.matching(sourceFileFilter) }
        )

    override fun source(vararg sources: Any) {
        sourceFiles.from(sources)
    }

    override fun setSource(vararg sources: Any) {
        sourceFiles.setFrom(sources)
    }

    fun disallowSourceChanges() {
        sourceFiles.disallowChanges()
    }

    @Internal
    final override fun getIncludes(): MutableSet<String> = sourceFileFilter.includes

    @Internal
    final override fun getExcludes(): MutableSet<String> = sourceFileFilter.excludes

    final override fun setIncludes(includes: Iterable<String>): PatternFilterable = also {
        sourceFileFilter.setIncludes(includes)
    }

    final override fun setExcludes(excludes: Iterable<String>): PatternFilterable = also {
        sourceFileFilter.setExcludes(excludes)
    }

    final override fun include(vararg includes: String): PatternFilterable = also {
        sourceFileFilter.include(*includes)
    }

    final override fun include(includes: Iterable<String>): PatternFilterable = also {
        sourceFileFilter.include(includes)
    }

    final override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable = also {
        sourceFileFilter.include(includeSpec)
    }

    final override fun include(includeSpec: Closure<*>): PatternFilterable = also {
        sourceFileFilter.include(includeSpec)
    }

    final override fun exclude(vararg excludes: String): PatternFilterable = also {
        sourceFileFilter.exclude(*excludes)
    }

    final override fun exclude(excludes: Iterable<String>): PatternFilterable = also {
        sourceFileFilter.exclude(excludes)
    }

    final override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable = also {
        sourceFileFilter.exclude(excludeSpec)
    }

    final override fun exclude(excludeSpec: Closure<*>): PatternFilterable = also {
        sourceFileFilter.exclude(excludeSpec)
    }
}

// taken from org.jetbrains.kotlin.gradle.utils (fileUtils.kt)
private fun String.fileExtensionCasePermutations(): List<String> {
    val lowercaseInput = lowercase()
    val length = lowercaseInput.length
    // number of permutations is 2^n
    val max = 1 shl length
    val result = mutableListOf<String>()
    var combination: CharArray
    for (i in 0 until max) {
        combination = lowercaseInput.toCharArray()
        for (j in 0 until length) {
            // If j-th bit is set, we convert it to upper case
            if (((i shr j) and 1) == 1) {
                combination[j] = combination[j].uppercaseChar()
            }
        }
        result.add(String(combination))
    }
    return result
}
