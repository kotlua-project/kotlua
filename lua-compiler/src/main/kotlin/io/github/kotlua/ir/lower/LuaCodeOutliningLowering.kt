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
package io.github.kotlua.ir.lower

import io.github.kotlua.LuaToStringGenerator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ast.scope.LuaRootScope
import io.github.kotlua.ir.PreSerializationLuaSymbols
import io.github.kotlua.ir.transformers.translateLuaCodeWithFreeNames
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.LoweringContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.ir.syntheticBodyIsNotSupported
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.parentDeclarationsWithSelf
import org.jetbrains.kotlin.ir.util.toIrConst
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class LuaCodeOutliningLowering(
    val loweringContext: LoweringContext,
) : BodyLoweringPass {
    private val symbols = loweringContext.symbols as PreSerializationLuaSymbols

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        if (!irBody.containsCallsTo(symbols.luaCode)) return

        val replacer = LuaCodeOutlineTransformer(loweringContext, container)
        irBody.transformChildrenVoid(replacer)

        val outlinedFunctions = replacer.outlinedFunctions
        if (outlinedFunctions.isEmpty()) return
        putOutlinedFunctionsIntoContainer(outlinedFunctions, container, irBody)
    }

    private fun putOutlinedFunctionsIntoContainer(
        outlinedFunctions: List<IrFunction>,
        container: IrDeclaration,
        irBody: IrBody,
    ) {
        for (outlinedFunction in outlinedFunctions) {
            outlinedFunction.parent = container.parentDeclarationsWithSelf.firstIsInstanceOrNull<IrDeclarationParent>()
                ?: compilationException("Unexpected container to insert the outlined function to", container)
        }

        when (irBody) {
            is IrBlockBody -> {
                irBody.statements.addAll(0, outlinedFunctions)
            }

            is IrExpressionBody -> {
                val builder = loweringContext.createIrBuilder(container.symbol)
                irBody.expression =
                    builder.irBlock(irBody.startOffset, irBody.endOffset) {
                        +outlinedFunctions
                        +irBody.expression
                    }
            }

            is IrSyntheticBody -> {
                syntheticBodyIsNotSupported(container)
            }
        }
    }

    companion object {
        val OUTLINED_LUA_CODE_ORIGIN = IrDeclarationOriginImpl("OUTLINED_LUA_CODE")
    }
}

private fun IrElement.containsCallsTo(symbol: IrFunctionSymbol): Boolean {
    var result = false
    acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (result) return
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol == symbol) {
                    result = true
                    return
                }
                super.visitCall(expression)
            }
        },
    )
    return result
}

private class LuaCodeOutlineTransformer(
    val loweringContext: LoweringContext,
    val container: IrDeclaration,
) : IrElementTransformerVoid() {
    private val symbols = loweringContext.symbols as PreSerializationLuaSymbols
    val outlinedFunctions = mutableListOf<IrFunction>()

    private val localScopes =
        ArrayDeque<HashMap<String, IrValueDeclaration>>().apply {
            addLast(hashMapOf())
        }

    init {
        if (container is IrFunction) {
            container.parameters.forEach {
                registerValueDeclaration(it)
            }
        }
    }

    inline fun <T> withLocalScope(body: () -> T): T {
        localScopes.addLast(hashMapOf())
        try {
            return body()
        } finally {
            localScopes.removeLast()
        }
    }

    fun registerValueDeclaration(irValueDeclaration: IrValueDeclaration) {
        val name = irValueDeclaration.name
        if (!name.isSpecial) {
            val currentScope =
                localScopes.lastOrNull()
                    ?: compilationException("Expecting a scope", irValueDeclaration)
            currentScope[name.identifier] = irValueDeclaration
        }
    }

    fun findValueDeclarationWithName(name: String): IrValueDeclaration? {
        for (i in (localScopes.size - 1) downTo 0) {
            localScopes[i][name]?.let { return it }
        }
        return null
    }

    override fun visitContainerExpression(expression: IrContainerExpression): IrExpression =
        withLocalScope { super.visitContainerExpression(expression) }

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement = withLocalScope { super.visitDeclaration(declaration) }

    override fun visitValueParameter(declaration: IrValueParameter): IrStatement =
        super.visitValueParameter(declaration).also { registerValueDeclaration(declaration) }

    override fun visitVariable(declaration: IrVariable): IrStatement =
        super.visitVariable(declaration).also { registerValueDeclaration(declaration) }

    override fun visitCall(expression: IrCall): IrExpression = outlineLuaCodeIfNeeded(expression) ?: super.visitCall(expression)

    private fun outlineLuaCodeIfNeeded(expression: IrCall): IrExpression? {
        if (expression.symbol != symbols.luaCode) return null

        val luaCodeArg =
            expression.arguments[0]
                ?: compilationException("Expected lua code string", expression)
        val (luaStatements, freeNames) = translateLuaCodeWithFreeNames(luaCodeArg) ?: return null

        val kotlinLocalsUsedInLua = linkedMapOf<LuaName, IrValueDeclaration>()
        for ((ident, luaName) in freeNames) {
            findValueDeclarationWithName(ident)?.let { irDecl ->
                kotlinLocalsUsedInLua[luaName] = irDecl
            }
        }

        if (kotlinLocalsUsedInLua.isEmpty()) return null

        val outlinedFunction = createOutlinedFunction(kotlinLocalsUsedInLua)
        outlinedFunctions += outlinedFunction

        val luaFunExpr = createLuaFunction(luaStatements, kotlinLocalsUsedInLua)
        val luaFunCode = LuaToStringGenerator.generateExpression(luaFunExpr)

        val annotation = addSpecialAnnotation(outlinedFunction)
        annotation.arguments[0] = luaFunCode.toIrConst(loweringContext.irBuiltIns.stringType)

        return with(loweringContext.createIrBuilder(container.symbol)) {
            irCall(outlinedFunction).apply {
                arguments.assignFrom(kotlinLocalsUsedInLua.values, ::irGet)
            }
        }
    }

    private fun addSpecialAnnotation(outlinedFunction: IrSimpleFunction): IrAnnotation {
        val builder = loweringContext.createIrBuilder(outlinedFunction.symbol)
        val annotation =
            builder.irAnnotation(
                symbols.luaOutlinedFunctionAnnotationSymbol.constructors.first(),
                typeArguments = emptyList(),
            )
        outlinedFunction.annotations += annotation
        return annotation
    }

    private fun createLuaFunction(
        luaStatements: List<LuaStatement>,
        kotlinLocalsUsedInLua: Map<LuaName, IrValueDeclaration>,
    ): LuaExpression.AnonymousFunction {
        val newStatements = luaStatements.toMutableList()
        when (val lastStatement = newStatements.lastOrNull()) {
            is LuaStatement.Return -> {
                Unit
            }

            is LuaExpression.FunctionCall, is LuaExpression.MethodCall -> {
                newStatements[newStatements.lastIndex] = LuaStatement.Return(listOf(lastStatement as LuaExpression))
            }

            else -> {
                newStatements += LuaStatement.Return(listOf(LuaExpression.NilLiteral))
            }
        }

        val fnScope = LuaFunctionScope(LuaRootScope("<outlined>"), "Outlined lua() call")
        return LuaExpression.AnonymousFunction(
            parameters = kotlinLocalsUsedInLua.keys.toList(),
            isVararg = false,
            block = LuaBlock(newStatements, fnScope),
            scope = fnScope,
        )
    }

    private fun createOutlinedFunction(kotlinLocalsUsedInLua: Map<LuaName, IrValueDeclaration>): IrSimpleFunction {
        val outlinedFunction =
            loweringContext.irFactory.buildFun {
                val containerName = (container as? IrDeclarationWithName)?.name?.asString()
                name = Name.identifier(containerName?.let { "${it}_outlinedLuaCode_" } ?: "outlinedLuaCode_")
                returnType = symbols.dynamicType
                visibility = DescriptorVisibilities.LOCAL
                isExternal = true
                origin = LuaCodeOutliningLowering.OUTLINED_LUA_CODE_ORIGIN
            }
        outlinedFunction.body = loweringContext.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET)

        kotlinLocalsUsedInLua.values.forEach { local ->
            outlinedFunction.addValueParameter {
                name = local.name
                type = local.type
            }
        }

        return outlinedFunction
    }
}
