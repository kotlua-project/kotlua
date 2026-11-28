package io.github.kotlua.gradle.worker

import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkParameters

interface KotlinLuaCompileWorkParameters : WorkParameters {
    val compilerArgs: ListProperty<String>
}