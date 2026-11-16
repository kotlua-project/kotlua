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
package io.github.kotlua.parser

import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ast.scope.LuaLocalScope
import io.github.kotlua.ast.scope.LuaRootScope
import io.github.kotlua.ast.scope.LuaScope

class LuaScopeContext(
    initialScope: LuaScope,
) {
    private val rootScope: LuaRootScope =
        generateSequence(initialScope) { it.parent }
            .filterIsInstance<LuaRootScope>()
            .firstOrNull()
            ?: error("Initial scope must have a LuaRootScope ancestor")

    private val scopes = ArrayDeque<LuaScope>().apply { addLast(initialScope) }

    val currentScope: LuaScope
        get() = scopes.last()

    fun enterFunction(description: String = "<lua function>"): LuaFunctionScope {
        val fnScope = LuaFunctionScope(currentScope, description)
        scopes.addLast(fnScope)
        return fnScope
    }

    fun exitFunction(): LuaFunctionScope {
        val popped = scopes.removeLast()
        check(popped is LuaFunctionScope) { "Expected LuaFunctionScope, got $popped" }
        return popped
    }

    fun enterBlock(description: String = "<lua block>"): LuaLocalScope {
        val blockScope = LuaLocalScope(currentScope, description)
        scopes.addLast(blockScope)
        return blockScope
    }

    fun exitBlock(): LuaLocalScope {
        val popped = scopes.removeLast()
        check(popped is LuaLocalScope) { "Expected LuaLocalScope, got $popped" }
        return popped
    }

    inline fun <T> withBlock(
        description: String = "<lua block>",
        action: (LuaLocalScope) -> T,
    ): T {
        val scope = enterBlock(description)
        try {
            return action(scope)
        } finally {
            exitBlock()
        }
    }

    inline fun <T> withFunction(
        description: String = "<lua function>",
        action: (LuaFunctionScope) -> T,
    ): T {
        val scope = enterFunction(description)
        try {
            return action(scope)
        } finally {
            exitFunction()
        }
    }

    fun declareLocal(identifier: String): LuaName = currentScope.shadowName(identifier)

    fun resolveName(identifier: String): LuaName = currentScope.findName(identifier) ?: rootScope.declareName(identifier)
}
