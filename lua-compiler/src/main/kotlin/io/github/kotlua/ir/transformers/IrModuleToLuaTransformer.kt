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
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.cli.pipeline.LuaCompiledResult
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.dce.runSimpleDce
import io.github.kotlua.ir.lower.mainFunctionWrapper
import io.github.kotlua.ir.utils.*
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.Name
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

        val importStatements = generateImportStatements(modules, context)

        val fileDeclarations =
            modules.flatMap { module ->
                module.files.flatMap { file ->
                    file.accept(IrFileToLuaTransformer(), context).statements
                }
            }

        val mainCallStatements =
            if (backendContext.callMain) {
                LuaMainFunctionDetector(backendContext)
                    .getMainFunctionOrNull(mainModule)
                    ?.mainFunctionWrapper
                    ?.let { wrapper ->
                        val genContext =
                            LuaGenerationContext(
                                staticContext = context,
                                currentScope = context.rootScope,
                                localNames = NameTable(LuaModuleReservedScope),
                            )
                        listOf(
                            LuaExpression.FunctionCall(
                                target = genContext.getRefForStaticDeclaration(wrapper),
                                args = emptyList(),
                            ),
                        )
                    }.orEmpty()
            } else {
                emptyList()
            }

        val classStatements = generateClassesInTopologicalOrder(modules, context)
        val footer = listOf(LuaStatement.Return(listOf(context.moduleTableName)))
        val statements =
            header + importStatements + fileDeclarations + classStatements + context.initializerBlock +
                mainCallStatements + footer

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
            } else if (decl is IrEnumEntry) {
                decl.correspondingClass?.let(::collect)
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

    private fun generateImportStatements(
        modules: Iterable<IrModuleFragment>,
        context: LuaStaticContext,
    ): List<LuaStatement> {
        val genContext =
            LuaGenerationContext(
                staticContext = context,
                currentScope = context.rootScope,
                localNames = NameTable(LuaModuleReservedScope),
            )
        val result = mutableListOf<LuaStatement>()
        val moduleVarCache = hashMapOf<String, LuaName>()
        val emittedDeclarations = hashSetOf<IrDeclaration>()
        val moduleNameTable = NameTable<String>(LuaModuleReservedScope)

        fun getOrCreateModuleVar(moduleName: String): LuaName =
            moduleVarCache.getOrPut(moduleName) {
                val baseName = moduleName.substringAfterLast('.').substringAfterLast('/')
                val varName = LuaName(moduleNameTable.declareFreshName(moduleName, "_imp_$baseName"))
                result +=
                    LuaStatement.LocalVarDeclaration(
                        names = listOf(varName),
                        initializers =
                            listOf(
                                LuaExpression.FunctionCall(
                                    target = LuaName("require"),
                                    args = listOf(LuaExpression.StringLiteral(moduleName)),
                                ),
                            ),
                    )
                varName
            }

        fun processDeclaration(decl: IrDeclaration) {
            if (decl !is IrDeclarationWithName || !decl.isEffectivelyExternal()) return
            if (!emittedDeclarations.add(decl)) return

            val importAnnotation =
                decl.getAnnotation(LuaStandardClassIds.Annotations.LuaImport.asSingleFqName())
                    ?: return

            val moduleArg =
                (importAnnotation.argumentMapping[Name.identifier("module")] as? IrConst)?.value as? String
            val codeExpr =
                parseLuaExpressionFromAnnotation(decl, LuaStandardClassIds.Annotations.LuaImport, "code")

            when {
                codeExpr != null -> {
                    val localName = genContext.getNameForStaticDeclaration(decl)
                    result +=
                        LuaStatement.LocalVarDeclaration(
                            names = listOf(localName),
                            initializers = listOf(codeExpr),
                        )
                }

                !moduleArg.isNullOrEmpty() -> {
                    if (decl is IrClass) {
                        val localName = genContext.getNameForStaticDeclaration(decl)
                        result +=
                            LuaStatement.LocalVarDeclaration(
                                names = listOf(localName),
                                initializers =
                                    listOf(
                                        LuaExpression.FunctionCall(
                                            target = LuaName("require"),
                                            args = listOf(LuaExpression.StringLiteral(moduleArg)),
                                        ),
                                    ),
                            )
                    } else {
                        val modVar = getOrCreateModuleVar(moduleArg)
                        context.externalImportModuleVars[decl] = modVar
                        if (decl is IrProperty) {
                            decl.getter?.let { context.externalImportModuleVars[it] = modVar }
                            decl.setter?.let { context.externalImportModuleVars[it] = modVar }
                        }
                    }
                }

                else -> {
                    compilationException("@LuaImport must specify either 'module' or 'code'", importAnnotation)
                }
            }
        }

        fun visitDecl(decl: IrDeclaration) {
            processDeclaration(decl)
            if (decl is IrClass) {
                decl.declarations.forEach(::visitDecl)
            }
        }

        for (module in modules) {
            for (file in module.files) {
                file.declarations.forEach(::visitDecl)
            }
        }

        return result
    }
}
