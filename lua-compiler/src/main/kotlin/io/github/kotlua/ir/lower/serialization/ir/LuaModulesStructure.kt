/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Copyright 2026 The kotlua Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Notice: This file has been modified from its original form in the Kotlin project.
 */
package io.github.kotlua.ir.lower.serialization.ir

import io.github.kotlua.ir.LuaDeserializedDescriptorFactory
import io.github.kotlua.ir.LuaFactories
import io.github.kotlua.library.isLuaStdlib
import org.jetbrains.kotlin.backend.common.LoadedKlibs
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.storage.LockBasedStorageManager

class LuaModulesStructure(
    val mainModulePath: String,
    val compilerConfiguration: CompilerConfiguration,
    val klibs: LoadedKlibs,
) {
    private val languageVersionSettings: LanguageVersionSettings = compilerConfiguration.languageVersionSettings

    private val storageManager: LockBasedStorageManager = LockBasedStorageManager("ModulesStructure")
    private var runtimeModule: ModuleDescriptorImpl? = null

    val descriptors: Map<KotlinLibrary, ModuleDescriptor>
        field = mutableMapOf<KotlinLibrary, ModuleDescriptorImpl>()

    init {
        val descriptors = klibs.all.map { getModuleDescriptorImpl(it) }
        val friendDescriptors = klibs.friends.mapTo(mutableSetOf(), ::getModuleDescriptorImpl)
        descriptors.forEach { descriptor ->
            descriptor.setDependencies(descriptors, friendDescriptors)
        }
    }

    private fun getModuleDescriptorImpl(current: KotlinLibrary): ModuleDescriptorImpl {
        if (current in descriptors) {
            return descriptors.getValue(current)
        }

        val isBuiltIns = current.isLuaStdlib

        val lookupTracker = compilerConfiguration[CommonConfigurationKeys.LOOKUP_TRACKER] ?: LookupTracker.DO_NOTHING

        val md =
            LuaFactories.LuaDeserializedDescriptorFactory.createDescriptorOptionalBuiltIns(
                current,
                languageVersionSettings,
                storageManager,
                runtimeModule?.builtIns,
                lookupTracker = lookupTracker,
            )
        if (isBuiltIns) runtimeModule = md

        descriptors[current] = md

        return md
    }

    fun getModuleDescriptor(current: KotlinLibrary): ModuleDescriptor = getModuleDescriptorImpl(current)
}
