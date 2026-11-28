package io.github.kotlua.gradle.worker

import org.gradle.api.GradleException
import org.gradle.workers.WorkAction

abstract class KotlinLuaCompileWorkAction : WorkAction<KotlinLuaCompileWorkParameters> {
    override fun execute() {
        val args = parameters.compilerArgs.get().toTypedArray()

        val compilerClass = Class.forName("io.github.kotlua.cli.compiler.K2LuaCompiler")
        val compiler = compilerClass.getDeclaredConstructor().newInstance()
        val cliCompilerClass = Class.forName("org.jetbrains.kotlin.cli.common.CLICompiler")
        val doMainNoExit = cliCompilerClass.getMethod(
            "doMainNoExit",
            cliCompilerClass,
            Array<String>::class.java,
        )

        val exitCode = doMainNoExit.invoke(null, compiler, args)
        val code = exitCode.javaClass.getMethod("getCode").invoke(exitCode) as Int
        if (code != 0) {
            throw GradleException("Kotlua compilation failed with exit code: $code")
        }
    }
}
