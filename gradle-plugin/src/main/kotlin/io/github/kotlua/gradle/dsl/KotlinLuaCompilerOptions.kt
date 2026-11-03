package io.github.kotlua.gradle.dsl

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions

interface KotlinLuaCompilerOptions : KotlinCommonCompilerOptions {
    @get:Optional
    @get:Input
    val moduleName: Property<String>

    @get:Optional
    @get:Input
    val nopack: Property<Boolean>
}
