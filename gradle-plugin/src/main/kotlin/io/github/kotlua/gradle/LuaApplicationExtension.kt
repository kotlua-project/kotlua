package io.github.kotlua.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

abstract class LuaApplicationExtension @Inject constructor(objects: ObjectFactory) {
    val entryFile: Property<String> = objects.property<String>().convention("main.lua")
    val args: ListProperty<String> = objects.listProperty<String>().convention(emptyList())
}