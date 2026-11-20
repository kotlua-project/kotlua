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

import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class LuaMultiReturnLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    companion object {
        val LUA_MULTI_ASSIGN_ORIGIN = IrStatementOriginImpl("LUA_MULTI_ASSIGN")
    }

    private val symbols = context.symbols
    private val irBuiltIns = context.irBuiltIns

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent

        if (container is IrSimpleFunction && container.isMultiReturnFunction()) {
            val returnClass = container.returnType.classOrNull?.owner
            val components = returnClass?.findComponentFunctions()
            if (returnClass != null && components != null) {
                lowerMultiReturnFunctionBody(irBody, container, returnClass, components)
            }
        }

        lowerMultiReturnCallSites(irBody, rootParent)
    }

    private fun IrFunction.isMultiReturnFunction(): Boolean = hasAnnotation(LuaStandardClassIds.Annotations.LuaMultiReturn)

    private fun IrClass.findComponentFunctions(): List<IrSimpleFunction>? {
        val primaryCtor = primaryConstructor ?: return null
        val count = primaryCtor.parameters.size
        if (count < 2) return null
        val functions = declarations.filterIsInstance<IrSimpleFunction>()
        return (1..count).map { index ->
            val expectedName = Name.identifier("component$index")
            functions.firstOrNull {
                it.name == expectedName &&
                    it.dispatchReceiverParameter != null &&
                    it.nonDispatchParameters.isEmpty()
            } ?: return null
        }
    }

    private fun lowerMultiReturnFunctionBody(
        irBody: IrBody,
        targetFunction: IrSimpleFunction,
        returnClass: IrClass,
        components: List<IrSimpleFunction>,
    ) {
        irBody.transformChildren(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: IrDeclarationParent,
                ): IrStatement {
                    val nextParent = (declaration as? IrDeclarationParent) ?: data
                    return super.visitDeclaration(declaration, nextParent)
                }

                override fun visitReturn(
                    expression: IrReturn,
                    data: IrDeclarationParent,
                ): IrExpression {
                    expression.transformChildren(this, data)
                    if (expression.returnTargetSymbol != targetFunction.symbol) {
                        return expression
                    }
                    return rewriteReturnValue(
                        value = expression.value,
                        targetSymbol = targetFunction.symbol,
                        returnClass = returnClass,
                        components = components,
                        parentScope = data,
                    ).also { it.patchDeclarationParents(data) }
                }
            },
            targetFunction,
        )
    }

    private fun rewriteReturnValue(
        value: IrExpression,
        targetSymbol: IrReturnTargetSymbol,
        returnClass: IrClass,
        components: List<IrSimpleFunction>,
        parentScope: IrDeclarationParent,
    ): IrExpression {
        if (value is IrConstructorCall && value.symbol.owner.constructedClass == returnClass) {
            val args =
                value.symbol.owner.nonDispatchParameters.map { param ->
                    value.arguments[param.indexInParameters] ?: irNull()
                }
            return buildLuaMultiReturn(args, value.startOffset, value.endOffset)
        }

        if (value is IrCall && value.symbol.owner.kotlinFqName
                .asString() == "kotlin.to"
        ) {
            val args =
                value.symbol.owner.parameters.map { param ->
                    value.arguments[param.indexInParameters] ?: irNull()
                }
            if (args.size == 2) {
                return buildLuaMultiReturn(args, value.startOffset, value.endOffset)
            }
        }

        if (value is IrCall && value.symbol.owner.isMultiReturnFunction()) {
            return IrReturnImpl(
                value.startOffset,
                value.endOffset,
                irBuiltIns.nothingType,
                targetSymbol,
                value,
            )
        }

        if (value is IrWhen) {
            val newBranches =
                value.branches.map { branch ->
                    val newResult = rewriteReturnValue(branch.result, targetSymbol, returnClass, components, parentScope)
                    if (branch is IrElseBranch) {
                        IrElseBranchImpl(branch.startOffset, branch.endOffset, branch.condition, newResult)
                    } else {
                        IrBranchImpl(branch.startOffset, branch.endOffset, branch.condition, newResult)
                    }
                }
            return IrWhenImpl(
                value.startOffset,
                value.endOffset,
                irBuiltIns.nothingType,
                value.origin,
                newBranches,
            )
        }

        if (value is IrContainerExpression && value.statements.isNotEmpty()) {
            val stmts = value.statements.dropLast(1).toMutableList()
            val last = value.statements.last()
            stmts +=
                if (last is IrExpression) {
                    rewriteReturnValue(last, targetSymbol, returnClass, components, parentScope)
                } else {
                    last
                }
            return IrBlockImpl(value.startOffset, value.endOffset, irBuiltIns.nothingType, value.origin, stmts)
        }

        val builder = context.createIrBuilder((parentScope as IrSymbolOwner).symbol, value.startOffset, value.endOffset)
        return builder.run {
            irBlock(value.startOffset, value.endOffset, resultType = irBuiltIns.nothingType) {
                val tmp = irTemporary(value, nameHint = "ret_tuple")
                val extracted =
                    components.map { comp ->
                        IrCallImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            comp.returnType,
                            comp.symbol,
                            typeArgumentsCount = 0,
                        ).apply {
                            dispatchReceiver = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, tmp.type, tmp.symbol)
                        }
                    }
                +buildLuaMultiReturn(extracted)
            }
        }
    }

    private fun lowerMultiReturnCallSites(
        irBody: IrBody,
        rootParent: IrDeclarationParent,
    ) {
        val usageCounts = hashMapOf<IrValueSymbol, Int>()
        irBody.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    usageCounts[expression.symbol] = (usageCounts[expression.symbol] ?: 0) + 1
                }
            },
        )

        val untouchedCalls = hashSetOf<IrCall>()

        fun processStatementList(statements: MutableList<IrStatement>) {
            var i = 0
            while (i < statements.size) {
                val tmpVar = statements[i] as? IrVariable
                val call = tmpVar?.initializer as? IrCall
                if (tmpVar != null && call != null && call.symbol.owner.isMultiReturnFunction()) {
                    val matched = mutableListOf<Pair<IrVariable, Int>>()
                    var j = i + 1
                    while (j < statements.size) {
                        val compMatch = matchComponentCall(statements[j], tmpVar.symbol) ?: break
                        matched += compMatch
                        j++
                    }

                    if (matched.isNotEmpty() && (usageCounts[tmpVar.symbol] ?: 0) == matched.size) {
                        val maxIndex = matched.maxOf { it.second }
                        val byIndex = matched.associate { it.second to it.first }
                        val slotStatements =
                            (1..maxIndex).map<Int, IrStatement> { idx ->
                                val targetVar = byIndex[idx]
                                if (targetVar != null) {
                                    targetVar.apply { initializer = null }
                                } else {
                                    IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)
                                }
                            }
                        untouchedCalls += call
                        val composite =
                            IrCompositeImpl(
                                startOffset = tmpVar.startOffset,
                                endOffset = tmpVar.endOffset,
                                type = irBuiltIns.unitType,
                                origin = LUA_MULTI_ASSIGN_ORIGIN,
                                statements = slotStatements + call,
                            )
                        statements.subList(i, j).clear()
                        statements.add(i, composite)
                        i++
                        continue
                    }
                }
                i++
            }
        }

        irBody.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitBlockBody(body: IrBlockBody) {
                    super.visitBlockBody(body)
                    processStatementList(body.statements)
                }

                override fun visitContainerExpression(expression: IrContainerExpression) {
                    super.visitContainerExpression(expression)
                    processStatementList(expression.statements)
                }
            },
        )

        irBody.transform(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: IrDeclarationParent,
                ): IrStatement {
                    val nextParent = (declaration as? IrDeclarationParent) ?: data
                    return super.visitDeclaration(declaration, nextParent)
                }

                override fun visitContainerExpression(
                    expression: IrContainerExpression,
                    data: IrDeclarationParent,
                ): IrExpression {
                    if (expression.origin == LUA_MULTI_ASSIGN_ORIGIN) {
                        val call = expression.statements.last() as IrCall
                        call.transformChildren(this, data)
                        return expression
                    }
                    return super.visitContainerExpression(expression, data)
                }

                override fun visitReturn(
                    expression: IrReturn,
                    data: IrDeclarationParent,
                ): IrExpression {
                    val value = expression.value
                    if (value is IrCall &&
                        value.symbol.owner.isMultiReturnFunction() &&
                        data is IrSimpleFunction &&
                        data.isMultiReturnFunction() &&
                        expression.returnTargetSymbol == data.symbol
                    ) {
                        value.transformChildren(this, data)
                        return expression
                    }
                    return super.visitReturn(expression, data)
                }

                override fun visitCall(
                    expression: IrCall,
                    data: IrDeclarationParent,
                ): IrExpression {
                    expression.transformChildren(this, data)
                    if (expression in untouchedCalls || !expression.symbol.owner.isMultiReturnFunction()) {
                        return expression
                    }

                    val returnClass = expression.type.classOrNull?.owner ?: return expression
                    val primaryCtor = returnClass.primaryConstructor ?: return expression
                    val componentCount = primaryCtor.parameters.size
                    if (componentCount < 2) return expression

                    val builder = context.createIrBuilder((data as IrSymbolOwner).symbol, expression.startOffset, expression.endOffset)
                    return builder
                        .run {
                            irBlock(expression.startOffset, expression.endOffset, resultType = expression.type) {
                                val packedVar = irTemporary(buildPackToTable(expression), nameHint = "multi_pack")
                                +IrConstructorCallImpl
                                    .fromSymbolOwner(
                                        startOffset = expression.startOffset,
                                        endOffset = expression.endOffset,
                                        type = expression.type,
                                        constructorSymbol = primaryCtor.symbol,
                                    ).apply {
                                        for (idx in 0 until componentCount) {
                                            arguments[idx] =
                                                IrTypeOperatorCallImpl(
                                                    UNDEFINED_OFFSET,
                                                    UNDEFINED_OFFSET,
                                                    primaryCtor.parameters[idx].type,
                                                    IrTypeOperator.IMPLICIT_CAST,
                                                    primaryCtor.parameters[idx].type,
                                                    buildTableGet(irGet(packedVar), idx + 1),
                                                )
                                        }
                                    }
                            }
                        }.also { it.patchDeclarationParents(data) }
                }
            },
            rootParent,
        )
    }

    private fun matchComponentCall(
        stmt: IrStatement,
        tmpSymbol: IrVariableSymbol,
    ): Pair<IrVariable, Int>? {
        val variable = stmt as? IrVariable ?: return null
        var init = variable.initializer ?: return null
        while (init is IrTypeOperatorCall &&
            (init.operator == IrTypeOperator.REINTERPRET_CAST || init.operator == IrTypeOperator.IMPLICIT_CAST)
        ) {
            init = init.argument
        }
        val call = init as? IrCall ?: return null
        val receiver = call.dispatchReceiver as? IrGetValue ?: return null
        if (receiver.symbol != tmpSymbol) return null
        val name =
            call.symbol.owner.name
                .asString()
        if (!name.startsWith("component")) return null
        val index = name.removePrefix("component").toIntOrNull() ?: return null
        if (index < 1) return null
        return variable to index
    }

    private fun buildLuaMultiReturn(
        values: List<IrExpression>,
        startOffset: Int = UNDEFINED_OFFSET,
        endOffset: Int = UNDEFINED_OFFSET,
    ): IrExpression {
        val varargExpr =
            IrVarargImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = irBuiltIns.arrayClass.typeWith(irBuiltIns.anyNType),
                varargElementType = irBuiltIns.anyNType,
                elements = values,
            )
        return IrCallImpl(
            startOffset = startOffset,
            endOffset = endOffset,
            type = irBuiltIns.nothingType,
            symbol = symbols.luaMultiReturn,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = varargExpr
        }
    }

    private fun buildPackToTable(expr: IrExpression): IrExpression =
        IrCallImpl(
            startOffset = expr.startOffset,
            endOffset = expr.endOffset,
            type = irBuiltIns.anyNType,
            symbol = symbols.luaPackToTable,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = expr
        }

    private fun buildTableGet(
        tableExpr: IrExpression,
        index: Int,
    ): IrExpression =
        IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = irBuiltIns.anyNType,
            symbol = symbols.luaTableGet,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = tableExpr
            arguments[1] = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, index)
        }

    private fun irNull(): IrExpression = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)
}
