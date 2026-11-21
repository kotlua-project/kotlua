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

import io.github.kotlua.ast.*
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.transformers.IrElementToLuaExpressionTransformer
import io.github.kotlua.ir.transformers.IrElementToLuaStatementTransformer
import io.github.kotlua.ir.transformers.translateLuaCodeIntoStatementList
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal fun translateCall(
    expression: IrCall,
    data: LuaGenerationContext,
    exprTransformer: IrElementToLuaExpressionTransformer,
): LuaExpression {
    data.staticContext.expressionIntrinsics[expression.symbol]?.let { intrinsic ->
        return intrinsic(expression, data)
    }

    val calledFunction = expression.symbol.owner
    if (calledFunction.isFunctionalInvoke()) {
        val dispatchParam = calledFunction.dispatchReceiverParameter
        if (dispatchParam != null) {
            val target =
                expression.arguments[dispatchParam.indexInParameters]
                    ?.accept(exprTransformer, data)
                    ?: compilationException("Functional invoke must have dispatch receiver", expression)
            val args =
                calledFunction.nonDispatchParameters.map { param ->
                    expression.arguments[param.indexInParameters]
                        ?.accept(exprTransformer, data)
                        ?: LuaExpression.NilLiteral
                }
            return LuaExpression.FunctionCall(target = target, args = args)
        }
    }

    val function = calledFunction.realOverrideTarget

    if (function.isEffectivelyExternal()) {
        val args = translateExternalArguments(expression, function, data, exprTransformer)
        val property = function.correspondingPropertySymbol?.owner
        if (property != null) {
            val target = resolveExternalPropertyTarget(expression, function, property, data, exprTransformer)
            return when (function) {
                property.getter -> {
                    target
                }

                property.setter -> {
                    val fnScope = LuaFunctionScope(data.currentScope)
                    val assignment = LuaStatement.Assignment(targets = listOf(target), values = listOf(args.single()))
                    val anonFn =
                        LuaExpression.AnonymousFunction(
                            parameters = emptyList(),
                            isVararg = false,
                            block = LuaBlock(listOf(assignment), fnScope),
                            scope = fnScope,
                        )
                    LuaExpression.FunctionCall(target = anonFn, args = emptyList())
                }

                else -> {
                    compilationException("Function must be an accessor of corresponding property", function)
                }
            }
        }

        val dispatchParam = function.dispatchReceiverParameter
        if (dispatchParam != null) {
            val receiver =
                expression.arguments[dispatchParam.indexInParameters]
                    ?.accept(exprTransformer, data)
                    ?: compilationException("Member call must have dispatch receiver", expression)
            val luaName = function.getEffectiveExternalLuaName()
            val parentClass = function.parentAsClass
            val useColon =
                when {
                    function.hasAnnotation(LuaStandardClassIds.Annotations.LuaMethod) -> true
                    function.hasAnnotation(LuaStandardClassIds.Annotations.LuaFunction) -> false
                    parentClass.kind == ClassKind.OBJECT -> false
                    else -> true
                }
            return if (useColon && luaName.isValidLuaIdentifier()) {
                LuaExpression.MethodCall(target = receiver, methodName = luaName, args = args)
            } else {
                LuaExpression.FunctionCall(
                    target = luaElementAccess(luaName, receiver),
                    args = if (useColon) listOf(receiver) + args else args,
                )
            }
        }

        return LuaExpression.FunctionCall(
            target = data.getRefForStaticDeclaration(function),
            args = args,
        )
    }

    val args =
        function.nonDispatchParameters.map { param ->
            expression.arguments[param.indexInParameters]
                ?.accept(exprTransformer, data)
                ?: LuaExpression.NilLiteral
        }

    val dispatchParam = function.dispatchReceiverParameter
    if (dispatchParam != null) {
        val receiver =
            expression.arguments[dispatchParam.indexInParameters]
                ?.accept(exprTransformer, data)
                ?: compilationException("Member call must have dispatch receiver", expression)
        val methodName = data.getNameForMemberFunction(function)

        if (function.visibility == DescriptorVisibilities.PRIVATE ||
            function.visibility == DescriptorVisibilities.PRIVATE_TO_THIS
        ) {
            val ownerRef = data.getRefForStaticDeclaration(function.parentAsClass)
            return LuaExpression.FunctionCall(
                target = luaElementAccess(methodName, ownerRef),
                args = listOf(receiver) + args,
            )
        }

        expression.superQualifierSymbol?.let { superQualifier ->
            val superClass =
                if (superQualifier.owner.isInterface) {
                    function.resolveFakeOverrideOrFail().parentAsClass
                } else {
                    superQualifier.owner
                }
            val superRef = data.getRefForStaticDeclaration(superClass)
            return LuaExpression.FunctionCall(
                target = luaElementAccess(methodName, superRef),
                args = listOf(receiver) + args,
            )
        }

        return LuaExpression.MethodCall(
            target = receiver,
            methodName = methodName.identifier,
            args = args,
        )
    }

    val target =
        if (function.parent is IrFunction) {
            data.getNameForValueDeclaration(function)
        } else {
            data.getRefForStaticDeclaration(function)
        }

    return LuaExpression.FunctionCall(target = target, args = args)
}

internal fun translateExternalArguments(
    expression: IrCall,
    function: IrSimpleFunction,
    data: LuaGenerationContext,
    exprTransformer: IrElementToLuaExpressionTransformer,
): List<LuaExpression> =
    buildList {
        for (param in function.nonDispatchParameters) {
            val argExpr = expression.arguments[param.indexInParameters]
            if (param.isVararg) {
                when (argExpr) {
                    null -> {
                        Unit
                    }

                    is IrVararg if argExpr.elements.none { it is IrSpreadElement } -> {
                        for (elem in argExpr.elements) {
                            add((elem as IrExpression).accept(exprTransformer, data))
                        }
                    }

                    else -> {
                        val arrLua = argExpr.accept(exprTransformer, data)
                        val unpackRef = data.getRefForStaticDeclaration(data.staticContext.backendContext.symbols.luaUnpack.owner)
                        add(LuaExpression.FunctionCall(target = unpackRef, args = listOf(arrLua)))
                    }
                }
            } else {
                add(argExpr?.accept(exprTransformer, data) ?: LuaExpression.NilLiteral)
            }
        }
    }

internal fun resolveExternalPropertyTarget(
    expression: IrCall,
    function: IrSimpleFunction,
    property: IrProperty,
    data: LuaGenerationContext,
    exprTransformer: IrElementToLuaExpressionTransformer,
): LuaLValue {
    val dispatchParam = function.dispatchReceiverParameter
    return if (dispatchParam != null) {
        val receiver =
            expression.arguments[dispatchParam.indexInParameters]
                ?.accept(exprTransformer, data)
                ?: compilationException("External property access must have dispatch receiver", expression)
        val propName = function.getEffectiveExternalLuaName()
        luaElementAccess(propName, receiver)
    } else {
        data.getRefForStaticDeclaration(property)
    }
}

private fun IrSimpleFunction.isFunctionalInvoke(): Boolean {
    if (name.asString() != "invoke") return false
    val parentClass = parentClassOrNull ?: return false
    val packageFqName = parentClass.packageFqName
    val className = parentClass.name.asString()
    return (packageFqName == StandardClassIds.BASE_KOTLIN_PACKAGE && className.startsWith("Function")) ||
        (packageFqName == StandardClassIds.BASE_REFLECT_PACKAGE && className.startsWith("KFunction"))
}

internal fun translateFunctionToAnonymousFunction(
    declaration: IrFunction,
    data: LuaGenerationContext,
): LuaExpression.AnonymousFunction {
    val fnScope = LuaFunctionScope(data.currentScope, declaration.name.asString())
    val fnContext = data.newLocalContext(declaration, fnScope)

    val params =
        buildList {
            if (declaration is IrConstructor) {
                fnContext.currentThisName?.let(::add)
            } else {
                declaration.dispatchReceiverParameter?.let { add(fnContext.getNameForValueDeclaration(it)) }
            }
            declaration.nonDispatchParameters.forEach { param ->
                add(fnContext.getNameForValueDeclaration(param))
            }
        }
    val bodyBlock =
        (declaration.body?.accept(IrElementToLuaStatementTransformer(), fnContext) as? LuaBlock)
            ?: LuaBlock(emptyList(), fnScope)

    val finalBlock =
        if (declaration is IrConstructor) {
            val thisName = fnContext.currentThisName
            if (thisName != null && bodyBlock.statements.lastOrNull() !is LuaStatement.Return) {
                bodyBlock.copy(statements = bodyBlock.statements + LuaStatement.Return(listOf(thisName)))
            } else {
                bodyBlock
            }
        } else {
            bodyBlock
        }

    return LuaExpression.AnonymousFunction(
        parameters = params,
        isVararg = false,
        block = finalBlock,
        scope = fnScope,
    )
}

internal fun luaElementAccess(
    name: LuaName,
    receiver: LuaExpression?,
): LuaLValue =
    when {
        receiver == null -> name
        name.identifier.isValidLuaIdentifier() -> LuaLValue.TableField(receiver, name.identifier)
        else -> LuaLValue.TableIndex(receiver, LuaExpression.StringLiteral(name.identifier))
    }

internal fun luaElementAccess(
    name: String,
    receiver: LuaExpression?,
): LuaLValue =
    when {
        receiver == null -> LuaName(makeValidLuaIdentifier(name))
        name.isValidLuaIdentifier() -> LuaLValue.TableField(receiver, name)
        else -> LuaLValue.TableIndex(receiver, LuaExpression.StringLiteral(name))
    }

internal fun resolveFieldAccess(
    expression: IrFieldAccessExpression,
    data: LuaGenerationContext,
    exprTransformer: IrElementToLuaExpressionTransformer,
): LuaLValue {
    val field = expression.symbol.owner
    val fieldParent = field.parent
    val receiver = expression.receiver?.accept(exprTransformer, data)

    if (fieldParent is IrClass && field.isEffectivelyExternal()) {
        val externalReceiver =
            receiver
                ?: compilationException(
                    "Expect expression.receiver to not be null",
                    expression,
                )
        return luaElementAccess(field.name.asString(), externalReceiver)
    }

    return if (receiver == null) {
        data.getRefForStaticDeclaration(field)
    } else {
        val fieldName = data.getNameForMemberField(field)
        luaElementAccess(fieldName, receiver)
    }
}

internal inline fun buildLuaIf(
    expression: IrWhen,
    data: LuaGenerationContext,
    parentScope: LuaScope,
    translateBranch: (IrExpression, LuaScope) -> LuaBlock,
): LuaStatement.If {
    val exprTransformer = IrElementToLuaExpressionTransformer()
    val branches = expression.branches

    val elseBranch: IrBranch? = branches.lastOrNull()?.takeIf { isElseBranch(it) }
    val conditionalBranches = if (elseBranch != null) branches.dropLast(1) else branches

    val first = conditionalBranches.first()
    val thenCondition = first.condition.accept(exprTransformer, data)
    val thenBlock = translateBranch(first.result, parentScope)

    val elseIfs =
        conditionalBranches.drop(1).map { branch ->
            LuaStatement.If.ElseIf(
                condition = branch.condition.accept(exprTransformer, data),
                block = translateBranch(branch.result, parentScope),
            )
        }

    val elseBlock = elseBranch?.let { translateBranch(it.result, parentScope) }

    return LuaStatement.If(
        condition = thenCondition,
        thenBlock = thenBlock,
        elseIfs = elseIfs,
        elseBlock = elseBlock,
    )
}

private var IrFunction.cachedOutlinedLuaCode: LuaExpression.AnonymousFunction? by irAttribute(copyByDefault = false)

fun IrFunction.getLuaCode(): LuaExpression.AnonymousFunction? {
    cachedOutlinedLuaCode?.let {
        return it
    }

    parseLuaFromAnnotation(this, LuaStandardClassIds.Annotations.LuaOutlinedFunction, "luaFunctionExpression")
        ?.let { (_, parsedLuaFunction) ->
            cachedOutlinedLuaCode = parsedLuaFunction
            return parsedLuaFunction
        }

    return null
}

private fun parseLuaFromAnnotation(
    declaration: IrDeclaration,
    annotationClassId: ClassId,
    annotationParam: String,
): Pair<IrAnnotation, LuaExpression.AnonymousFunction>? {
    val annotation =
        declaration.getAnnotation(annotationClassId.asSingleFqName())
            ?: return null
    val luaCode =
        annotation.argumentMapping[Name.identifier(annotationParam)]
            ?: compilationException("@${annotationClassId.shortClassName} annotation must contain the Lua code argument", annotation)
    val statements =
        translateLuaCodeIntoStatementList(luaCode)
            ?: compilationException("Could not parse Lua code", annotation)

    val parsedLuaFunction =
        (statements.singleOrNull() as? LuaStatement.Return)
            ?.values
            ?.singleOrNull() as? LuaExpression.AnonymousFunction
            ?: compilationException("Provided Lua code is not a lua function", annotation)

    return annotation to parsedLuaFunction
}

internal fun parseLuaExpressionFromAnnotation(
    declaration: IrDeclaration,
    annotationClassId: ClassId,
    annotationParam: String,
): LuaExpression? {
    val annotation =
        declaration.getAnnotation(annotationClassId.asSingleFqName())
            ?: return null
    val luaCode =
        annotation.argumentMapping[Name.identifier(annotationParam)]
            ?: return null

    val wrappedExpr =
        if (luaCode is IrConst && luaCode.kind == IrConstKind.String) {
            val raw = (luaCode.value as String).trim()
            if (raw.isEmpty()) return null
            val withReturn = if (raw.startsWith("return ") || raw.startsWith("return\t")) raw else "return $raw"
            IrConstImpl.string(luaCode.startOffset, luaCode.endOffset, luaCode.type, withReturn)
        } else {
            luaCode
        }

    val statements =
        translateLuaCodeIntoStatementList(wrappedExpr)
            ?: compilationException("Could not parse Lua code in @${annotationClassId.shortClassName}", annotation)

    return (statements.singleOrNull() as? LuaStatement.Return)?.values?.singleOrNull()
        ?: (statements.singleOrNull() as? LuaExpression)
        ?: compilationException("Provided Lua code in @${annotationClassId.shortClassName} is not a Lua expression", annotation)
}

internal fun IrDeclarationWithName.getLuaNameOrNull(): String? {
    val ann = getAnnotation(LuaStandardClassIds.Annotations.LuaName.asSingleFqName())
    val custom = (ann?.argumentMapping?.get(Name.identifier("name")) as? IrConst)?.value as? String
    if (!custom.isNullOrEmpty()) return custom
    if (this is IrSimpleFunction) {
        correspondingPropertySymbol?.owner?.let { prop ->
            val propAnn = prop.getAnnotation(LuaStandardClassIds.Annotations.LuaName.asSingleFqName())
            val propCustom = (propAnn?.argumentMapping?.get(Name.identifier("name")) as? IrConst)?.value as? String
            if (!propCustom.isNullOrEmpty()) return propCustom
        }
    }
    return null
}

internal fun IrDeclarationWithName.getEffectiveExternalLuaName(): String =
    getLuaNameOrNull() ?: run {
        if (this is IrSimpleFunction) {
            correspondingPropertySymbol?.owner?.name?.asString() ?: name.asString()
        } else {
            name.asString()
        }
    }

internal fun isIgnoredClass(
    klass: IrClass,
    backendContext: LuaIrBackendContext,
): Boolean {
    if (klass.isEffectivelyExternal() || klass.isExpect) return true
    val symbols = backendContext.symbols
    val irBuiltIns = backendContext.irBuiltIns
    if (klass.symbol == symbols.genericSharedVariableBox.klass ||
        klass.symbol == symbols.defaultConstructorMarker
    ) {
        return true
    }
    val builtInPrimitives =
        setOf(
            irBuiltIns.anyClass,
            irBuiltIns.unitClass,
            irBuiltIns.nothingClass,
            irBuiltIns.booleanClass,
            irBuiltIns.charClass,
            irBuiltIns.byteClass,
            irBuiltIns.shortClass,
            irBuiltIns.intClass,
            irBuiltIns.longClass,
            irBuiltIns.floatClass,
            irBuiltIns.doubleClass,
            irBuiltIns.stringClass,
            irBuiltIns.numberClass,
        )
    if (klass.symbol in builtInPrimitives) return true

    val pkg = klass.packageFqName
    val name = klass.name.asString()
    return (pkg == StandardClassIds.BASE_KOTLIN_PACKAGE && name.startsWith("Function")) ||
        (pkg == StandardClassIds.BASE_REFLECT_PACKAGE && name.startsWith("KFunction"))
}
