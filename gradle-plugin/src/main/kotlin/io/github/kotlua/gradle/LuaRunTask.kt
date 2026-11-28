package io.github.kotlua.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

@UntrackedTask(because = "Executes a Lua script with side effects and interactive stdin")
abstract class LuaRunTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Option(option = "entry", description = "Lua entry file relative to outputDir")
    abstract val entryFile: Property<String>

    @get:Input
    abstract val programArgs: ListProperty<String>

    @get:Input
    @get:Optional
    @get:Option(option = "args", description = "Additional command-line arguments passed to the Lua script")
    abstract val commandLineArgs: Property<String>

    @get:Internal
    abstract val binariesDirectories: ConfigurableFileCollection

    init {
        entryFile.convention("main.lua")
        programArgs.convention(emptyList())
    }

    @TaskAction
    fun run() {
        val dir = outputDir.get().asFile
        val requestedEntry = entryFile.get()
        val scriptFile = dir.resolve(requestedEntry)

        val dirPath = dir.canonicalFile.toPath()
        val scriptPath = scriptFile.canonicalFile.toPath()
        if (!scriptPath.startsWith(dirPath)) {
            throw GradleException(
                "Lua entry file '$requestedEntry' must resolve inside ${dir.absolutePath}.",
            )
        }

        if (!scriptFile.isFile) {
            val available = dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("lua", ignoreCase = true) }
                .map { it.toRelativeString(dir).replace(File.separatorChar, '/') }
                .sorted()
                .toList()
                .ifEmpty { listOf("<none>") }
                .joinToString(", ")

            throw GradleException(
                "Lua entry file '$requestedEntry' not found in ${dir.absolutePath}. " +
                        "Available .lua files: $available",
            )
        }

        val executable = findLuaExecutable()
        val cliArgs = programArgs.get() + splitArgs(commandLineArgs.orNull)

        execOperations.exec {
            workingDir = dir
            this.executable = executable
            standardInput = System.`in`
            args(listOf(requestedEntry) + cliArgs)
        }
    }

    private fun findLuaExecutable(): String {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.startsWith("windows")
        val osSubdir = when {
            isWindows -> "windows"
            osName.startsWith("mac") -> "macos"
            else -> "linux"
        }

        val searchedBinaryDirs = binariesDirectories.files.distinct()
        for (baseDir in searchedBinaryDirs) {
            if (!baseDir.isDirectory) continue
            for (dir in listOf(baseDir.resolve(osSubdir), baseDir)) {
                findLuaInBinaryDir(dir, isWindows)?.let { return it.absolutePath }
            }
        }

        val pathEnv = System.getenv("PATH").orEmpty()
        val pathNames = listOf("lua5.1", "lua51", "luajit", "lua")
        val extensions = if (isWindows) WINDOWS_EXECUTABLE_EXTENSIONS else listOf("")

        for (rawEntry in pathEnv.split(File.pathSeparator)) {
            val entry = rawEntry.trim().trim('"')
            if (entry.isBlank()) continue
            val dir = File(entry)
            if (!dir.isDirectory) continue

            for (name in pathNames) {
                for (ext in extensions) {
                    val file = File(dir, "$name$ext")
                    if (file.isFile && (isWindows || file.canExecute())) {
                        return file.absolutePath
                    }
                }
            }
        }

        val dirsText = searchedBinaryDirs.joinToString(", ") { it.absolutePath }
        throw GradleException(
            buildString {
                appendLine("Lua interpreter not found.")
                appendLine("Checked:")
                appendLine("  - Project binaries directories: $dirsText (and '$osSubdir/' subdirectory)")
                appendLine("  - System PATH (${pathNames.joinToString(", ")})")
                append("Install Lua 5.1 / LuaJIT on PATH or place the binary into 'binaries/' (or 'binaries/$osSubdir/') in your project.")
            },
        )
    }

    private fun findLuaInBinaryDir(dir: File, isWindows: Boolean): File? {
        if (!dir.isDirectory) return null
        val files = dir.listFiles()?.filter { it.isFile } ?: return null

        return files
            .mapNotNull { file ->
                val candidateName = if (isWindows) {
                    stripWindowsExtension(file.name) ?: return@mapNotNull null
                } else {
                    if (!file.canExecute()) return@mapNotNull null
                    file.name
                }
                val priority = luaExecutablePriority(candidateName) ?: return@mapNotNull null
                file to priority
            }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .firstOrNull()
            ?.first
    }

    private fun stripWindowsExtension(fileName: String): String? {
        val lower = fileName.lowercase()
        val ext = WINDOWS_EXECUTABLE_EXTENSIONS.firstOrNull { lower.endsWith(it) } ?: return null
        return fileName.dropLast(ext.length)
    }

    private fun luaExecutablePriority(baseName: String): Int? {
        return when (val lower = baseName.lowercase()) {
            "lua5.1" -> 0
            "lua51" -> 1
            "luajit" -> 2
            "lua" -> 3
            else -> {
                if (lower.startsWith("luajit-") &&
                    !lower.endsWith(".so") &&
                    !lower.contains(".so.") &&
                    !lower.endsWith(".dylib") &&
                    !lower.endsWith(".lua")
                ) {
                    2
                } else {
                    null
                }
            }
        }
    }

    private companion object {
        val WINDOWS_EXECUTABLE_EXTENSIONS = listOf(".exe", ".bat", ".cmd")
    }
}

private fun splitArgs(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var hasToken = false
    var i = 0

    while (i < raw.length) {
        val ch = raw[i]
        when {
            ch == '\\' && i + 1 < raw.length -> {
                val next = raw[i + 1]
                if (next == '"' || next == '\'' || next == '\\') {
                    current.append(next)
                    hasToken = true
                    i += 2
                    continue
                } else {
                    current.append(ch)
                    hasToken = true
                }
            }
            quote != null -> {
                if (ch == quote) {
                    quote = null
                } else {
                    current.append(ch)
                }
                hasToken = true
            }
            ch == '"' || ch == '\'' -> {
                quote = ch
                hasToken = true
            }
            ch.isWhitespace() -> {
                if (hasToken) {
                    result.add(current.toString())
                    current.clear()
                    hasToken = false
                }
            }
            else -> {
                current.append(ch)
                hasToken = true
            }
        }
        i++
    }

    if (quote != null) {
        throw GradleException("Unclosed quote ($quote) in command-line arguments: $raw")
    }
    if (hasToken) {
        result.add(current.toString())
    }
    return result
}
