/*
 * Copyright 2026 The kotlua Authors
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
 */
package io.github.kotlua.ir.utils

import io.github.kotlua.ast.LuaName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.utils.DFS

class LuaNameLinkingNamer : LuaIrNamerBase() {
    private val constructorNameMap = mutableMapOf<IrConstructor, LuaName>()
    private val constructorNameTables = mutableMapOf<IrClass, NameTable<IrConstructor>>()

    private val staticNameMap = mutableMapOf<IrDeclaration, LuaName>()
    private val memberFunctionNameMap = mutableMapOf<String, LuaName>()
    private val memberFieldNameMap = mutableMapOf<IrField, LuaName>()
    private val fieldDataCache = mutableMapOf<IrClass, Map<IrField, String>>()
    private val publicNames = NameTable<IrDeclaration>()
    private val privateNames = NameTable<IrDeclaration>()

    fun isPrivateToModule(declaration: IrDeclaration): Boolean =
        when (declaration) {
            is IrField -> {
                true
            }

            is IrDeclarationWithVisibility -> {
                declaration.visibility == DescriptorVisibilities.PRIVATE ||
                    declaration.visibility == DescriptorVisibilities.PRIVATE_TO_THIS ||
                    declaration.visibility == DescriptorVisibilities.LOCAL
            }

            else -> {
                false
            }
        }

    override fun getNameForConstructor(constructor: IrConstructor): LuaName =
        constructorNameMap.getOrPut(constructor) {
            val table = constructorNameTables.getOrPut(constructor.parentAsClass) { NameTable() }
            LuaName(table.declareFreshName(constructor, calculateLuaConstructorSignature(constructor)))
        }

    override fun getNameForStaticDeclaration(declaration: IrDeclarationWithName): LuaName =
        staticNameMap.getOrPut(declaration) {
            val suggestedName = declaration.nameIfPropertyAccessor() ?: declaration.name.asString()
            val table = if (isPrivateToModule(declaration)) privateNames else publicNames
            LuaName(table.declareFreshName(declaration, suggestedName))
        }

    override fun getNameForMemberFunction(function: IrSimpleFunction): LuaName {
        require(function.dispatchReceiverParameter != null) {
            "Function '${function.name}' has no dispatch receiver"
        }
        val signature = luaFunctionSignature(function)
        return memberFunctionNameMap.getOrPut(signature) { LuaName(signature) }
    }

    override fun getNameForMemberField(field: IrField): LuaName {
        require(!field.isStatic) {
            "Static field '${field.name}' should use getNameForStaticDeclaration"
        }
        return memberFieldNameMap.getOrPut(field) {
            val fieldName = field.parentAsClass.fieldData().getValue(field)
            LuaName(fieldName)
        }
    }

    private fun IrClass.fieldData(): Map<IrField, String> =
        fieldDataCache.getOrPut(this) {
            val nameCnt = hashMapOf<String, Int>()
            val result = hashMapOf<IrField, String>()

            val allClasses =
                DFS.topologicalOrder(listOf(this)) { node ->
                    node.superTypes.mapNotNull { superType ->
                        ((superType as? IrSimpleType)?.classifier as? IrClassSymbol)?.owner
                    }
                }

            allClasses.reversed().forEach { klass ->
                klass.declarations.forEach { declaration ->
                    when (declaration) {
                        is IrField if !declaration.isStatic -> {
                            val safeName = declaration.safeFieldName()
                            val suffix = nameCnt.getOrDefault(safeName, 0) + 1
                            nameCnt[safeName] = suffix
                            result[declaration] = "${safeName}_$suffix"
                        }

                        is IrFunction if declaration.dispatchReceiverParameter != null -> {
                            nameCnt[luaFunctionSignature(declaration)] = 1
                        }
                    }
                }
            }

            result
        }
}

private fun IrField.safeFieldName(): String =
    makeValidLuaIdentifier(name.asString()).let {
        if (it.lastOrNull()?.isDigit() == true) "${it}_" else it
    }
