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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.parentAsClass

interface LuaIrNamer {
    fun getNameForStaticDeclaration(declaration: IrDeclarationWithName): LuaName

    fun getNameForStaticFunction(function: IrSimpleFunction): LuaName

    fun getNameForMemberFunction(function: IrSimpleFunction): LuaName

    fun getNameForMemberField(field: IrField): LuaName

    fun getNameForField(field: IrField): LuaName

    fun getNameForClass(klass: IrClass): LuaName

    fun getNameForConstructor(constructor: IrConstructor): LuaName
}

abstract class LuaIrNamerBase : LuaIrNamer {
    override fun getNameForStaticFunction(function: IrSimpleFunction): LuaName = getNameForStaticDeclaration(function)

    override fun getNameForField(field: IrField): LuaName =
        if (field.isStatic || field.parent is IrScript) {
            getNameForStaticDeclaration(field)
        } else {
            getNameForMemberField(field)
        }

    override fun getNameForClass(klass: IrClass): LuaName = getNameForStaticDeclaration(klass)

    override fun getNameForConstructor(constructor: IrConstructor): LuaName = getNameForStaticDeclaration(constructor.parentAsClass)
}
