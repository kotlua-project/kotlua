package io.github.kotlua.gradle.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import javax.inject.Inject

internal abstract class KotlinLuaCompilerOptionsDefault @Inject constructor(
    objectFactory: ObjectFactory
) : KotlinLuaCompilerOptions {

    override val apiVersion: Property<KotlinVersion> =
        objectFactory.property(KotlinVersion::class.java)

    override val languageVersion: Property<KotlinVersion> =
        objectFactory.property(KotlinVersion::class.java)

    override val optIn: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList<String>())

    override val progressiveMode: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    override val allWarningsAsErrors: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    override val extraWarnings: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    override val suppressWarnings: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    override val verbose: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    override val freeCompilerArgs: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList<String>())

    override val moduleName: Property<String> =
        objectFactory.property<String>()

}