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

import org.jetbrains.kotlin.backend.common.serialization.DeclarationTable
import org.jetbrains.kotlin.backend.common.serialization.IrFileSerializer
import org.jetbrains.kotlin.backend.common.serialization.IrSerializationSettings
import org.jetbrains.kotlin.ir.declarations.IrFile

fun interface LuaIrFileMetadataFactory {
    fun createLuaIrFileMetadata(irFile: IrFile): LuaIrFileMetadata
}

object LuaIrFileEmptyMetadataFactory : LuaIrFileMetadataFactory {
    override fun createLuaIrFileMetadata(irFile: IrFile): LuaIrFileMetadata = LuaIrFileMetadata()
}

class LuaIrFileSerializer(
    settings: IrSerializationSettings,
    declarationTable: DeclarationTable.Default,
    private val luaIrFileMetadataFactory: LuaIrFileMetadataFactory,
) : IrFileSerializer(settings, declarationTable) {
    override fun backendSpecificMetadata(irFile: IrFile) = luaIrFileMetadataFactory.createLuaIrFileMetadata(irFile)
}
