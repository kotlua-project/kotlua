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
package io.github.kotlua.library.metadata

import io.github.kotlua.library.isLuaStdlib
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.functionInterfacePackageFragmentProvider
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.components.metadata
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibMetadataModuleDescriptorFactory
import org.jetbrains.kotlin.library.metadata.parseModuleHeader
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.CommonCompilerDeserializationConfiguration
import org.jetbrains.kotlin.storage.StorageManager

class LuaKlibMetadataModuleDescriptorFactory(
    private val delegate: KlibMetadataModuleDescriptorFactory,
) : KlibMetadataModuleDescriptorFactory by delegate {
    override fun createDescriptorOptionalBuiltIns(
        library: KotlinLibrary,
        languageVersionSettings: LanguageVersionSettings,
        storageManager: StorageManager,
        builtIns: KotlinBuiltIns?,
        lookupTracker: LookupTracker,
    ): ModuleDescriptorImpl {
        if (!library.isLuaStdlib) {
            return delegate.createDescriptorOptionalBuiltIns(
                library,
                languageVersionSettings,
                storageManager,
                builtIns,
                lookupTracker,
            )
        }

        val libraryProto = parseModuleHeader(library.metadata.moduleHeaderData)
        val moduleName = Name.special(libraryProto.moduleName)
        val moduleOrigin = DeserializedKlibModuleOrigin(library)

        val moduleDescriptor =
            if (builtIns != null) {
                delegate.descriptorFactory.createDescriptor(moduleName, storageManager, builtIns, moduleOrigin)
            } else {
                delegate.descriptorFactory.createDescriptorAndNewBuiltIns(moduleName, storageManager, moduleOrigin)
            }

        val provider =
            delegate.createPackageFragmentProvider(
                library = library,
                customMetadataProtoLoader = null,
                storageManager = storageManager,
                moduleDescriptor = moduleDescriptor,
                configuration = CommonCompilerDeserializationConfiguration(languageVersionSettings),
                compositePackageFragmentAddend = functionInterfacePackageFragmentProvider(storageManager, moduleDescriptor),
                lookupTracker = lookupTracker,
            )

        moduleDescriptor.initialize(provider)
        return moduleDescriptor
    }
}
