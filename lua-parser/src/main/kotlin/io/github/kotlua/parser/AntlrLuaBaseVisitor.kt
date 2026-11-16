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

import io.github.kotlua.parser.generated.Lua51BaseVisitor
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ParseTree

internal abstract class AntlrLuaBaseVisitor<TResult> : Lua51BaseVisitor<TResult>() {
    override fun defaultResult(): TResult {
        error("Not supported")
    }

    protected inline fun <reified T> visitNode(node: ParseTree): T = visit(node).expect<T>(node)

    protected inline fun <reified T> visitAll(nodes: List<ParseTree>): List<T> = nodes.map { visitNode<T>(it) }

    protected inline fun <reified T> TResult?.expect(node: ParseTree? = null): T {
        if (this !is T) {
            val ctxInfo = (node as? ParserRuleContext)?.let { " at ${it.start?.line}:${it.start?.charPositionInLine}" }.orEmpty()
            error("Expected ${T::class.simpleName}, got ${this?.let { it::class.simpleName }}$ctxInfo")
        }
        return this
    }
}
