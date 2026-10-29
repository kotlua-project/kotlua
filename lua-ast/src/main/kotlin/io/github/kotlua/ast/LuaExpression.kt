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
package io.github.kotlua.ast

sealed interface LuaExpression : LuaNode {
    data object NilLiteral : LuaExpression

    data class BooleanLiteral(
        val value: Boolean,
    ) : LuaExpression

    data class IntLiteral(
        val value: Int,
    ) : LuaExpression

    data class LongLiteral(
        val value: Long,
    ) : LuaExpression

    data class DoubleLiteral(
        val value: Double,
    ) : LuaExpression

    data class StringLiteral(
        val value: String,
    ) : LuaExpression

    data object VarargLiteral : LuaExpression

    data class BinaryOp(
        val left: LuaExpression,
        val op: LuaBinaryOperator,
        val right: LuaExpression,
    ) : LuaExpression

    data class UnaryOp(
        val op: LuaUnaryOperator,
        val operand: LuaExpression,
    ) : LuaExpression

    data class TableConstructor(
        val fields: List<LuaField>,
    ) : LuaExpression

    data class AnonymousFunction(
        val parameters: List<String>,
        val isVararg: Boolean,
        val block: LuaBlock,
    ) : LuaExpression

    data class FunctionCall(
        val target: LuaExpression,
        val args: List<LuaExpression>,
    ) : LuaExpression,
        LuaStatement

    data class MethodCall(
        val target: LuaExpression,
        val methodName: String,
        val args: List<LuaExpression>,
    ) : LuaExpression,
        LuaStatement
}
