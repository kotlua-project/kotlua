package io.github.kotlua.gradle.dsl

import io.github.kotlua.cli.compiler.K2LuaCompilerArguments

internal object KotlinLuaCompilerOptionsHelper {
    internal fun fillCompilerArguments(
        from: KotlinLuaCompilerOptions,
        args: K2LuaCompilerArguments,
    ) {
        args.apiVersion = from.apiVersion.orNull?.version
        args.languageVersion = from.languageVersion.orNull?.version
        args.optIn = from.optIn.get().toTypedArray()
        args.progressiveMode = from.progressiveMode.get()
        args.allWarningsAsErrors = from.allWarningsAsErrors.get()
        args.extraWarnings = from.extraWarnings.get()
        args.suppressWarnings = from.suppressWarnings.get()
        args.verbose = from.verbose.get()
        args.freeArgs += from.freeCompilerArgs.get()
        args.nopack = from.nopack.get()
    }
}