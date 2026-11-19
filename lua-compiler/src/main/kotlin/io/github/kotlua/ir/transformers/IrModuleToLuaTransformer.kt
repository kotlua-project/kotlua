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
package io.github.kotlua.ir.transformers

import io.github.kotlua.LuaTextOutput
import io.github.kotlua.LuaToStringGenerator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaField
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.cli.pipeline.LuaCompiledResult
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.dce.runSimpleDce
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.LuaModuleReservedScope
import io.github.kotlua.ir.utils.LuaStaticContext
import io.github.kotlua.ir.utils.NameTable
import io.github.kotlua.ir.utils.isIgnoredClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.utils.DFS

class IrModuleToLuaTransformer(
    private val backendContext: LuaIrBackendContext,
) {
    fun generateModule(
        mainModule: IrModuleFragment,
        modules: Iterable<IrModuleFragment>,
    ): LuaCompiledResult {
        val context = LuaStaticContext(backendContext)
        runSimpleDce(modules, mainModule, backendContext)

        val header =
            listOf(
                LuaStatement.LocalVarDeclaration(
                    names = listOf(context.moduleTableName),
                    initializers = listOf(LuaExpression.TableConstructor(emptyList())),
                ),
                LuaStatement.LocalVarDeclaration(
                    names = listOf(context.privateTableName),
                    initializers = listOf(LuaExpression.TableConstructor(emptyList())),
                ),
            )

        val fileDeclarations =
            modules.flatMap { module ->
                module.files.flatMap { file ->
                    file.accept(IrFileToLuaTransformer(), context).statements
                }
            }

        val classStatements = generateClassesInTopologicalOrder(modules, context)
        val footer = listOf(LuaStatement.Return(listOf(context.moduleTableName)))
        val statements = header + fileDeclarations + classStatements + context.initializerBlock + footer

        val textOutput = LuaTextOutput()
        val luaCode =
            LuaToStringGenerator.generate(
                LuaBlock(statements, context.rootScope),
                textOutput,
            )

        return LuaCompiledResult(
            mainFileName = "main.lua",
            luaSourceCode = luaCode,
        )
    }

    private fun generateClassesInTopologicalOrder(
        modules: Iterable<IrModuleFragment>,
        context: LuaStaticContext,
    ): List<LuaStatement> {
        val allClasses = mutableListOf<IrClass>()

        fun collect(decl: IrDeclaration) {
            if (decl is IrClass && !isIgnoredClass(decl, backendContext)) {
                allClasses += decl
                decl.declarations.forEach(::collect)
            }
        }

        for (module in modules) {
            for (file in module.files) {
                file.declarations.forEach(::collect)
            }
        }

        if (allClasses.isEmpty()) return emptyList()

        val classSet = allClasses.toHashSet()
        val sortedClasses =
            DFS
                .topologicalOrder(allClasses) { klass ->
                    klass.superTypes.mapNotNull { superType ->
                        ((superType as? IrSimpleType)?.classifier as? IrClassSymbol)?.owner?.takeIf { it in classSet }
                    }
                }.reversed()

        val genContext =
            LuaGenerationContext(
                staticContext = context,
                currentScope = context.rootScope,
                localNames = NameTable(LuaModuleReservedScope),
            )
        val declTransformer = IrDeclarationToLuaTransformer()
        val defineClassRef = genContext.getRefForStaticDeclaration(backendContext.symbols.luaDefineClass.owner)

        return sortedClasses.flatMap { klass ->
            val superClasses =
                klass.superTypes.mapNotNull { superType ->
                    ((superType as? IrSimpleType)?.classifier as? IrClassSymbol)?.owner
                }
            val superClass =
                superClasses.firstOrNull {
                    !it.isInterface && !isIgnoredClass(it, backendContext) && it.symbol != backendContext.irBuiltIns.anyClass
                }
            val interfaces =
                superClasses.filter {
                    it.isInterface && !isIgnoredClass(it, backendContext)
                }

            val superExpr = superClass?.let { genContext.getRefForStaticDeclaration(it) } ?: LuaExpression.NilLiteral
            val interfacesExpr =
                if (interfaces.isEmpty()) {
                    LuaExpression.NilLiteral
                } else {
                    LuaExpression.TableConstructor(
                        interfaces.map { LuaField.Positional(genContext.getRefForStaticDeclaration(it)) },
                    )
                }

            val classDef =
                LuaStatement.Assignment(
                    targets = listOf(genContext.getRefForStaticDeclaration(klass)),
                    values =
                        listOf(
                            LuaExpression.FunctionCall(
                                target = defineClassRef,
                                args = listOf(superExpr, interfacesExpr),
                            ),
                        ),
                )

            val memberStatements =
                klass.declarations.mapNotNull { member ->
                    when (member) {
                        is IrSimpleFunction -> member.accept(declTransformer, genContext)
                        is IrConstructor -> member.accept(declTransformer, genContext)
                        is IrField if member.isStatic -> member.accept(declTransformer, genContext)
                        else -> null
                    }
                }

            listOf(classDef) + memberStatements
        }
    }
}
