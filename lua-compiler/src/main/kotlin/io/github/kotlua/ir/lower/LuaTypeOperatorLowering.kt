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
import io.github.kotlua.ir.lower.inline.RemoveInlineDeclarationsWithReifiedTypeParametersLowering
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isCharSequence
import org.jetbrains.kotlin.ir.types.isComparable
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.ir.types.isNumber
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isFunctionOrKFunction
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.ir.util.isSuspendFunction
import org.jetbrains.kotlin.ir.util.isTrueConst
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.shallowCopyOrNull
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name

@PhasePrerequisites(
    RemoveInlineDeclarationsWithReifiedTypeParametersLowering::class,
    LuaCallableReferenceLowering::class,
)
class LuaTypeOperatorLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val irBuiltIns = context.irBuiltIns
    private val symbols = context.symbols
    private val unitType = irBuiltIns.unitType
    private val booleanType = irBuiltIns.booleanType
    private val stringType = irBuiltIns.stringType
    private val unitValue
        get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, irBuiltIns.unitClass)

    private val litTrue: IrExpression
        get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, true)
    private val litFalse: IrExpression
        get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, false)
    private val litNull: IrExpression
        get() = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent
        var tmpCounter = 0

        irBody.transformChildren(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: IrDeclarationParent,
                ) = super.visitDeclaration(declaration, declaration as? IrDeclarationParent ?: data)

                override fun visitCall(
                    expression: IrCall,
                    data: IrDeclarationParent,
                ): IrElement {
                    val call = super.visitCall(expression, data)
                    if (call is IrCall && call.symbol == irBuiltIns.checkNotNullSymbol) {
                        val arg = call.arguments[0]!!
                        return buildEnsureNotNull(
                            value = arg,
                            resultType = call.type,
                            startOffset = call.startOffset,
                            endOffset = call.endOffset,
                            origin = call.origin,
                        )
                    }
                    return call
                }

                override fun visitTypeOperator(
                    expression: IrTypeOperatorCall,
                    data: IrDeclarationParent,
                ): IrExpression {
                    super.visitTypeOperator(expression, data)

                    return when (expression.operator) {
                        IrTypeOperator.IMPLICIT_CAST,
                        IrTypeOperator.IMPLICIT_DYNAMIC_CAST,
                        -> {
                            expression.wrapWithUnsafeCast(expression.argument)
                        }

                        IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                            lowerCoercionToUnit(expression)
                        }

                        IrTypeOperator.IMPLICIT_NOTNULL -> {
                            buildEnsureNotNull(
                                value = expression.argument,
                                resultType = expression.typeOperand,
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                            )
                        }

                        IrTypeOperator.INSTANCEOF -> {
                            lowerInstanceOf(expression, data, inverted = false)
                        }

                        IrTypeOperator.NOT_INSTANCEOF -> {
                            lowerInstanceOf(expression, data, inverted = true)
                        }

                        IrTypeOperator.CAST -> {
                            lowerCast(expression, data, isSafe = false)
                        }

                        IrTypeOperator.SAFE_CAST -> {
                            lowerCast(expression, data, isSafe = true)
                        }

                        IrTypeOperator.IMPLICIT_INTEGER_COERCION,
                        IrTypeOperator.REINTERPRET_CAST,
                        IrTypeOperator.SAM_CONVERSION,
                        -> {
                            expression
                        }
                    }
                }

                private fun IrTypeOperatorCall.wrapWithUnsafeCast(arg: IrExpression): IrExpression =
                    IrTypeOperatorCallImpl(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = type,
                        operator = IrTypeOperator.REINTERPRET_CAST,
                        typeOperand = typeOperand,
                        argument = arg,
                    )

                private fun lowerCoercionToUnit(expression: IrTypeOperatorCall): IrExpression =
                    IrCompositeImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = unitType,
                        origin = null,
                        statements = listOf(expression.argument, unitValue),
                    )

                private fun lowerInstanceOf(
                    expression: IrTypeOperatorCall,
                    declarationParent: IrDeclarationParent,
                    inverted: Boolean,
                ): IrExpression {
                    val newStatements = mutableListOf<IrStatement>()
                    val argument = cacheValue(expression.argument, newStatements, declarationParent)
                    val check = generateTypeCheck(argument, expression.typeOperand, isSafeCast = false)
                    val result = if (inverted) buildBooleanNot(check) else check
                    newStatements += result
                    return IrCompositeImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = booleanType,
                        origin = null,
                        statements = newStatements,
                    )
                }

                private fun lowerCast(
                    expression: IrTypeOperatorCall,
                    declarationParent: IrDeclarationParent,
                    isSafe: Boolean,
                ): IrExpression {
                    val toType = expression.typeOperand
                    val failResult =
                        if (isSafe) {
                            litNull
                        } else {
                            IrCallImpl(
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                                type = irBuiltIns.nothingType,
                                symbol = symbols.throwTypeCastException,
                                typeArgumentsCount = 0,
                            )
                        }

                    val newStatements = mutableListOf<IrStatement>()
                    val isNotNullAssertionRequired = !isSafe && expression.argument.type.isNullable() && !toType.isNullable()
                    val initialArg =
                        if (isNotNullAssertionRequired) {
                            buildEnsureNotNull(
                                value = expression.argument,
                                resultType = toType,
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                            )
                        } else {
                            expression.argument
                        }

                    val argument = cacheValue(initialArg, newStatements, declarationParent)
                    val check = generateTypeCheck(argument, toType, isSafeCast = isSafe)

                    if (check.isTrueConst()) {
                        return if (newStatements.isEmpty()) {
                            expression.wrapWithUnsafeCast(argument())
                        } else {
                            newStatements += expression.wrapWithUnsafeCast(argument())
                            IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, newStatements)
                        }
                    }

                    val castedValue = expression.wrapWithUnsafeCast(argument())
                    newStatements += buildIfElse(expression.type, check, castedValue, failResult)
                    return IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, newStatements)
                }

                private fun cacheValue(
                    value: IrExpression,
                    newStatements: MutableList<IrStatement>,
                    declarationParent: IrDeclarationParent,
                ): () -> IrExpression {
                    val isImmutableLocalGet =
                        value is IrGetValue &&
                            (value.symbol.owner as? IrVariable)?.isVar != true
                    return if (value is IrConst || isImmutableLocalGet) {
                        { value.deepCopyWithSymbols() }
                    } else {
                        val tmpVar =
                            IrVariableImpl(
                                startOffset = value.startOffset,
                                endOffset = value.endOffset,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                symbol = IrVariableSymbolImpl(),
                                name = Name.identifier("tmp\$type\$${tmpCounter++}"),
                                type = value.type,
                                isVar = false,
                                isConst = false,
                                isLateinit = false,
                            ).apply {
                                parent = declarationParent
                                initializer = value
                            }
                        newStatements += tmpVar
                        {
                            IrGetValueImpl(
                                startOffset = UNDEFINED_OFFSET,
                                endOffset = UNDEFINED_OFFSET,
                                type = tmpVar.type,
                                symbol = tmpVar.symbol,
                            )
                        }
                    }
                }

                private fun generateTypeCheck(
                    argument: () -> IrExpression,
                    toType: IrType,
                    isSafeCast: Boolean,
                ): IrExpression {
                    val toNotNullable = toType.makeNotNull()
                    val argumentInstance = argument()
                    val fromType = argumentInstance.type
                    val fromNotNullable = fromType.makeNotNull()

                    val isFromNullable = fromType.isNullable()
                    val isToNullable = toType.isNullable()

                    if (fromNotNullable !is IrDynamicType &&
                        fromNotNullable.isSubtypeOf(toNotNullable, context.typeSystem)
                    ) {
                        return when {
                            isFromNullable && isSafeCast -> litTrue
                            isFromNullable && !isToNullable -> buildBooleanNot(nullCheck(argument()))
                            else -> litTrue
                        }
                    }

                    val instanceCheck = generateTypeCheckNonNull(argumentInstance, toNotNullable)

                    return when {
                        !isFromNullable -> {
                            instanceCheck
                        }

                        isToNullable && !isSafeCast -> {
                            if (instanceCheck.isTrueConst()) {
                                litTrue
                            } else {
                                buildOrOr(nullCheck(argument()), instanceCheck)
                            }
                        }

                        else -> {
                            instanceCheck
                        }
                    }
                }

                private fun generateTypeCheckNonNull(
                    argument: IrExpression,
                    toType: IrType,
                ): IrExpression =
                    when {
                        toType is IrDynamicType -> {
                            argument
                        }

                        toType.isAny() -> {
                            buildBooleanNot(nullCheck(argument))
                        }

                        toType.isNothing() -> {
                            IrCompositeImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                booleanType,
                                null,
                                listOf(argument, litFalse),
                            )
                        }

                        toType.isString() -> {
                            buildIsTypeOf(argument, "string")
                        }

                        toType.isBoolean() -> {
                            buildIsTypeOf(argument, "boolean")
                        }

                        toType.isFunctionOrKFunction() -> {
                            buildIsTypeOf(argument, "function")
                        }

                        toType.isFloat() || toType.isDouble() -> {
                            buildIsTypeOf(argument, "number")
                        }

                        toType.isLong() -> {
                            buildUnaryCall(symbols.luaIsLong, argument)
                        }

                        toType.isNumber() -> {
                            buildOrOr(
                                buildIsTypeOf(argument, "number"),
                                buildUnaryCall(symbols.luaIsLong, argument.deepCopyWithSymbols()),
                            )
                        }

                        toType.isInt() -> {
                            buildUnaryCall(symbols.luaIsInt, argument)
                        }

                        toType.isByte() -> {
                            buildUnaryCall(symbols.luaIsByte, argument)
                        }

                        toType.isShort() -> {
                            buildUnaryCall(symbols.luaIsShort, argument)
                        }

                        toType.isChar() -> {
                            buildIsChar(argument)
                        }

                        toType.isCharSequence() -> {
                            buildOrOr(
                                buildIsTypeOf(argument, "string"),
                                buildIsInstance(argument.deepCopyWithSymbols(), toType),
                            )
                        }

                        toType.isFunctionOrKFunction() || toType.isSuspendFunction() -> {
                            buildIsTypeOf(argument, "function")
                        }

                        toType.isComparable() -> {
                            buildComparableCheck(argument, toType)
                        }

                        toType.isTypeParameter() -> {
                            generateTypeCheckWithTypeParameter(argument, toType)
                        }

                        (toType.classifierOrNull?.owner as? IrClass)?.isEffectivelyExternal() == true -> {
                            buildBooleanNot(
                                nullCheck(argument),
                            )
                        }

                        else -> {
                            buildIsInstance(argument, toType)
                        }
                    }

                private fun generateTypeCheckWithTypeParameter(
                    argument: IrExpression,
                    toType: IrType,
                ): IrExpression {
                    val typeParameterSymbol =
                        (toType.classifierOrNull as? IrTypeParameterSymbol)
                            ?: compilationException("Expected type parameter, but got $toType", argument)

                    return typeParameterSymbol.owner.superTypes
                        .filter { !it.isNullableAny() }
                        .fold<IrType, IrExpression?>(null) { acc, superType ->
                            val copy = argument.shallowCopyOrNull() ?: argument.deepCopyWithSymbols()
                            val check = generateTypeCheckNonNull(copy, superType.makeNotNull())
                            if (acc == null) check else buildAndAnd(acc, check)
                        } ?: if (toType.isNullable()) litTrue else buildBooleanNot(nullCheck(argument))
                }

                private fun buildComparableCheck(
                    argument: IrExpression,
                    toType: IrType,
                ): IrExpression {
                    val isNum = buildIsTypeOf(argument, "number")
                    val isLong = buildUnaryCall(symbols.luaIsLong, argument.deepCopyWithSymbols())
                    val isStr = buildIsTypeOf(argument.deepCopyWithSymbols(), "string")
                    val isBool = buildIsTypeOf(argument.deepCopyWithSymbols(), "boolean")
                    val isInst = buildIsInstance(argument.deepCopyWithSymbols(), toType)
                    return buildOrOr(buildOrOr(isNum, isLong), buildOrOr(buildOrOr(isStr, isBool), isInst))
                }

                private fun buildIsChar(argument: IrExpression): IrExpression {
                    val isStr = buildIsTypeOf(argument, "string")
                    val len =
                        IrCallImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            irBuiltIns.intType,
                            symbols.luaLength,
                            typeArgumentsCount = 0,
                        ).apply {
                            arguments[0] = argument.deepCopyWithSymbols()
                        }
                    val lenIsOne =
                        IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, irBuiltIns.eqeqSymbol, typeArgumentsCount = 0).apply {
                            arguments[0] = len
                            arguments[1] = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, 1)
                        }
                    return buildAndAnd(isStr, lenIsOne)
                }

                private fun buildIsTypeOf(
                    argument: IrExpression,
                    typeName: String,
                ): IrExpression =
                    IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, symbols.luaIsTypeOf, typeArgumentsCount = 0).apply {
                        arguments[0] = argument
                        arguments[1] = IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, stringType, typeName)
                    }

                private fun buildIsInstance(
                    argument: IrExpression,
                    toType: IrType,
                ): IrExpression {
                    val classRef =
                        IrCallImpl(
                            startOffset = UNDEFINED_OFFSET,
                            endOffset = UNDEFINED_OFFSET,
                            type = irBuiltIns.anyNType,
                            symbol = symbols.luaClass,
                            typeArgumentsCount = 1,
                        ).apply {
                            typeArguments[0] = toType
                        }
                    return IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        booleanType,
                        symbols.luaIsInstance,
                        typeArgumentsCount = 0,
                    ).apply {
                        arguments[0] = argument
                        arguments[1] = classRef
                    }
                }

                private fun buildUnaryCall(
                    symbol: IrSimpleFunctionSymbol,
                    argument: IrExpression,
                ): IrExpression =
                    IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, symbol, typeArgumentsCount = 0).apply {
                        arguments[0] = argument
                    }

                private fun nullCheck(value: IrExpression): IrExpression =
                    IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, irBuiltIns.eqeqSymbol, typeArgumentsCount = 0).apply {
                        arguments[0] = value
                        arguments[1] = litNull
                    }

                private fun buildBooleanNot(value: IrExpression): IrExpression =
                    IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, irBuiltIns.booleanNotSymbol, typeArgumentsCount = 0).apply {
                        arguments[0] = value
                    }

                private fun buildAndAnd(
                    lhs: IrExpression,
                    rhs: IrExpression,
                ): IrExpression =
                    IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, IrStatementOrigin.ANDAND).apply {
                        branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, lhs, rhs)
                        branches += IrElseBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, litTrue, litFalse)
                    }

                private fun buildOrOr(
                    lhs: IrExpression,
                    rhs: IrExpression,
                ): IrExpression =
                    IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, IrStatementOrigin.OROR).apply {
                        branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, lhs, litTrue)
                        branches += IrElseBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, litTrue, rhs)
                    }

                private fun buildIfElse(
                    type: IrType,
                    cond: IrExpression,
                    thenExpr: IrExpression,
                    elseExpr: IrExpression,
                ): IrExpression =
                    IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, null).apply {
                        branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, cond, thenExpr)
                        branches += IrElseBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, litTrue, elseExpr)
                    }

                private fun buildEnsureNotNull(
                    value: IrExpression,
                    resultType: IrType,
                    startOffset: Int,
                    endOffset: Int,
                    origin: IrStatementOrigin? = null,
                ): IrExpression {
                    val notNullType = resultType.makeNotNull()
                    return IrCallImpl(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = notNullType,
                        symbol = symbols.ensureNotNull,
                        typeArgumentsCount = 1,
                        origin = origin,
                    ).apply {
                        typeArguments[0] = notNullType
                        arguments[0] = value
                    }
                }
            },
            rootParent,
        )
    }
}
