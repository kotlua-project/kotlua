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
package io.github.kotlua.ast.scope

import io.github.kotlua.ast.LuaName

abstract class LuaScope(
    val parent: LuaScope? = null,
    val description: String,
) {
    private val names: MutableMap<String, LuaName> = linkedMapOf()

    val ownNames: Map<String, LuaName>
        get() = names

    open fun declareName(identifier: String): LuaName = findOwnName(identifier) ?: doCreateName(identifier)

    open fun shadowName(identifier: String): LuaName = doCreateName(identifier)

    fun findName(identifier: String): LuaName? = findOwnName(identifier) ?: parent?.findName(identifier)

    fun hasOwnName(identifier: String): Boolean = identifier in names

    fun hasName(identifier: String): Boolean = hasOwnName(identifier) || (parent?.hasName(identifier) == true)

    protected open fun findOwnName(identifier: String): LuaName? = names[identifier]

    protected open fun doCreateName(identifier: String): LuaName = LuaName(identifier).also { names[identifier] = it }

    final override fun toString(): String = if (parent != null) "$description->$parent" else description
}

class LuaRootScope(
    description: String = "<global>",
) : LuaScope(parent = null, description = description)

class LuaFunctionScope(
    parent: LuaScope,
    description: String = "<lua function>",
) : LuaScope(parent = parent, description = description)

class LuaLocalScope(
    parent: LuaScope,
    description: String = "<lua block>",
) : LuaScope(parent = parent, description = description)
