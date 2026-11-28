package io.github.kotlua.gradle

import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptions
import io.github.kotlua.gradle.dsl.KotlinLuaCompilerOptionsDefault
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import javax.inject.Inject

abstract class KotluaExtension @Inject constructor(objects: ObjectFactory) : HasConfigurableKotlinCompilerOptions<KotlinLuaCompilerOptions> {
    override val compilerOptions: KotlinLuaCompilerOptions = objects.newInstance<KotlinLuaCompilerOptionsDefault>()

    val multiPlatformEnabled: Property<Boolean> = objects.property<Boolean>().convention(false)
}
