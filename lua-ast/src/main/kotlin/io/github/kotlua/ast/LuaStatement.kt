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

sealed interface LuaStatement : LuaNode {
    data class LocalVarDeclaration(
        val names: List<String>,
        val initializers: List<LuaExpression>,
    ) : LuaStatement {
        init {
            require(names.isNotEmpty())
        }
    }

    data class Assignment(
        val targets: List<LuaLValue>,
        val values: List<LuaExpression>,
    ) : LuaStatement

    data class If(
        val condition: LuaExpression,
        val thenBlock: LuaBlock,
        val elseIfs: List<ElseIf> = emptyList(),
        val elseBlock: LuaBlock? = null,
    ) : LuaStatement {
        data class ElseIf(
            val condition: LuaExpression,
            val block: LuaBlock,
        )
    }

    data class While(
        val condition: LuaExpression,
        val body: LuaBlock,
    ) : LuaStatement

    data class RepeatUntil(
        val body: LuaBlock,
        val condition: LuaExpression,
    ) : LuaStatement

    data object Break : LuaStatement

    data class Return(
        val values: List<LuaExpression> = emptyList(),
    ) : LuaStatement
}
