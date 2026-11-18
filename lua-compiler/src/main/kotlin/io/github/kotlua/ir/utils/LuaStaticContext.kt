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
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaRootScope
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.transformers.LuaIntrinsicTransformers

class LuaStaticContext(
    val backendContext: LuaIrBackendContext,
    val irNamer: LuaIrNamer = LuaNameLinkingNamer(),
    val rootScope: LuaScope = LuaRootScope(),
) : LuaIrNamer by irNamer {
    val intrinsics = LuaIntrinsicTransformers(backendContext)
    val initializerBlock = mutableListOf<LuaStatement>()

    val moduleTableName = LuaName(LuaModuleReservedScope.MODULE_TABLE_NAME)
    val privateTableName = LuaName(LuaModuleReservedScope.PRIVATE_TABLE_NAME)
}
