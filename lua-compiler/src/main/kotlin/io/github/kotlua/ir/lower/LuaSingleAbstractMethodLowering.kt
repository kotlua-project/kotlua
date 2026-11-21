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
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

class LuaSingleAbstractMethodLowering(
    private val context: LuaIrBackendContext,
) : FileLoweringPass {
    private val irBuiltIns = context.irBuiltIns
    private var samCounter = 0

    override fun lower(irFile: IrFile) {
        var currentFunction: IrFunction? = null
        val newTopLevelClasses = mutableListOf<IrClass>()

        irFile.transformChildren(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitFunction(
                    declaration: IrFunction,
                    data: IrDeclarationParent,
                ): IrFunction {
                    val prevFunction = currentFunction
                    currentFunction = declaration
                    try {
                        return super.visitFunction(declaration, declaration) as IrFunction
                    } finally {
                        currentFunction = prevFunction
                    }
                }

                override fun visitClass(
                    declaration: IrClass,
                    data: IrDeclarationParent,
                ): IrClass = super.visitClass(declaration, declaration) as IrClass

                override fun visitTypeOperator(
                    expression: IrTypeOperatorCall,
                    data: IrDeclarationParent,
                ): IrExpression {
                    val transformed = super.visitTypeOperator(expression, data)
                    if (transformed !is IrTypeOperatorCall || transformed.operator != IrTypeOperator.SAM_CONVERSION) {
                        return transformed
                    }
                    return lowerSamConversion(
                        expression = transformed,
                        parent = data,
                        irFile = irFile,
                        inFunction = currentFunction != null,
                        newTopLevelClasses = newTopLevelClasses,
                    )
                }
            },
            irFile,
        )

        irFile.declarations.addAll(newTopLevelClasses)
    }

    private fun lowerSamConversion(
        expression: IrTypeOperatorCall,
        parent: IrDeclarationParent,
        irFile: IrFile,
        inFunction: Boolean,
        newTopLevelClasses: MutableList<IrClass>,
    ): IrExpression {
        val samType = expression.typeOperand
        val samSuperType = samType.makeNotNull()
        val samClass =
            samSuperType.classOrNull?.owner
                ?: compilationException("SAM type must be an interface or class", expression)
        val abstractMethod = getSingleAbstractMethod(samClass, expression)

        val argument = expression.argument
        val argType = argument.type
        val nonNullArgType = argType.makeNotNull()

        val classParent = if (inFunction) parent else irFile
        val wrapperClass =
            context.irFactory.buildClass {
                startOffset = expression.startOffset
                endOffset = expression.endOffset
                origin = IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION
                name = Name.identifier("SamWrapper\$${samCounter++}")
                kind = ClassKind.CLASS
                visibility = if (inFunction) DescriptorVisibilities.LOCAL else DescriptorVisibilities.PRIVATE
                modality = Modality.FINAL
            }
        wrapperClass.parent = classParent
        wrapperClass.superTypes = listOf(samSuperType)
        wrapperClass.createThisReceiverParameter()

        val functionField =
            wrapperClass.addField {
                startOffset = expression.startOffset
                endOffset = expression.endOffset
                origin = IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION
                name = Name.identifier("function")
                type = nonNullArgType
                visibility = DescriptorVisibilities.PRIVATE
                isFinal = true
            }

        val ctor =
            wrapperClass.addConstructor {
                startOffset = expression.startOffset
                endOffset = expression.endOffset
                origin = IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION
                isPrimary = true
                visibility = DescriptorVisibilities.PUBLIC
            }
        val ctorParam =
            ctor.addValueParameter {
                name = Name.identifier("function")
                type = nonNullArgType
            }
        val anyCtor =
            irBuiltIns.anyClass.owner.constructors
                .single()
        ctor.body =
            context.irFactory.createBlockBody(expression.startOffset, expression.endOffset).apply {
                statements +=
                    IrDelegatingConstructorCallImpl(
                        expression.startOffset,
                        expression.endOffset,
                        irBuiltIns.unitType,
                        anyCtor.symbol,
                        0,
                    )
                statements +=
                    IrInstanceInitializerCallImpl(
                        expression.startOffset,
                        expression.endOffset,
                        wrapperClass.symbol,
                        irBuiltIns.unitType,
                    )
                statements +=
                    IrSetFieldImpl(
                        expression.startOffset,
                        expression.endOffset,
                        functionField.symbol,
                        IrGetValueImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            wrapperClass.defaultType,
                            wrapperClass.thisReceiver!!.symbol,
                        ),
                        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, ctorParam.type, ctorParam.symbol),
                        irBuiltIns.unitType,
                    )
            }

        val substitutor = createTypeSubstitutor(samClass, samSuperType)
        val samMethod =
            wrapperClass.addFunction {
                startOffset = expression.startOffset
                endOffset = expression.endOffset
                origin = IrDeclarationOrigin.GENERATED_SAM_IMPLEMENTATION
                name = abstractMethod.name
                returnType = substitutor?.substitute(abstractMethod.returnType) ?: abstractMethod.returnType
                modality = Modality.FINAL
                visibility = abstractMethod.visibility
                isSuspend = abstractMethod.isSuspend
            }
        samMethod.overriddenSymbols = listOf(abstractMethod.symbol)
        val dispatchParam = wrapperClass.thisReceiver!!.copyTo(samMethod, type = wrapperClass.defaultType)
        samMethod.parameters =
            listOf(dispatchParam) +
            abstractMethod.nonDispatchParameters.map { param ->
                param.copyTo(
                    samMethod,
                    type = substitutor?.substitute(param.type) ?: param.type,
                    defaultValue = null,
                )
            }

        val fnClass =
            nonNullArgType.classOrNull?.owner
                ?: compilationException("SAM argument must have a Function type", expression)
        val invokeMethod =
            fnClass.declarations
                .filterIsInstance<IrSimpleFunction>()
                .single { it.name == OperatorNameConventions.INVOKE }

        val getFunctionField =
            IrGetFieldImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                functionField.symbol,
                nonNullArgType,
                IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, dispatchParam.type, dispatchParam.symbol),
            )
        val invokeCall =
            IrCallImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = samMethod.returnType,
                symbol = invokeMethod.symbol,
                typeArgumentsCount = 0,
            ).apply {
                arguments[0] = getFunctionField
                samMethod.nonDispatchParameters.forEachIndexed { idx, param ->
                    arguments[idx + 1] = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, param.type, param.symbol)
                }
            }

        val samStatements =
            if (samMethod.returnType.isUnit()) {
                listOf(
                    invokeCall,
                    IrReturnImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        irBuiltIns.nothingType,
                        samMethod.symbol,
                        IrGetObjectValueImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            irBuiltIns.unitType,
                            irBuiltIns.unitClass,
                        ),
                    ),
                )
            } else {
                listOf(
                    IrReturnImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        irBuiltIns.nothingType,
                        samMethod.symbol,
                        invokeCall,
                    ),
                )
            }
        samMethod.body =
            context.irFactory.createBlockBody(expression.startOffset, expression.endOffset).apply {
                statements += samStatements
            }

        fun createWrapperCall(argExpr: IrExpression): IrExpression =
            IrConstructorCallImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = samSuperType,
                symbol = ctor.symbol,
                typeArgumentsCount = 0,
                constructorTypeArgumentsCount = 0,
            ).apply {
                arguments[0] = argExpr
            }

        val instantiation =
            if (argType.isNullable()) {
                val tmpVar =
                    IrVariableImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        symbol = IrVariableSymbolImpl(),
                        name = Name.identifier("tmp\$sam\$${samCounter++}"),
                        type = argType,
                        isVar = false,
                        isConst = false,
                        isLateinit = false,
                    ).apply {
                        this.parent = parent
                        initializer = argument
                    }
                val isNullCheck =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        irBuiltIns.booleanType,
                        irBuiltIns.eqeqSymbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        arguments[0] = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, tmpVar.type, tmpVar.symbol)
                        arguments[1] = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)
                    }
                val whenExpr =
                    IrWhenImpl(expression.startOffset, expression.endOffset, expression.type, null).apply {
                        branches +=
                            IrBranchImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                isNullCheck,
                                IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType),
                            )
                        branches +=
                            IrElseBranchImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.booleanType, true),
                                createWrapperCall(IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, tmpVar.type, tmpVar.symbol)),
                            )
                    }
                IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, listOf(tmpVar, whenExpr))
            } else {
                createWrapperCall(argument)
            }

        return if (inFunction) {
            IrBlockImpl(
                expression.startOffset,
                expression.endOffset,
                expression.type,
                null,
                listOf(wrapperClass, instantiation),
            )
        } else {
            newTopLevelClasses += wrapperClass
            instantiation
        }
    }

    private fun getSingleAbstractMethod(
        irClass: IrClass,
        expression: IrTypeOperatorCall,
    ): IrSimpleFunction =
        irClass.declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { it.modality == Modality.ABSTRACT }
            ?: compilationException("Class ${irClass.name} is not a SAM interface", expression)

    private fun createTypeSubstitutor(
        samClass: IrClass,
        samType: IrType,
    ): IrTypeSubstitutor? {
        val typeParams = samClass.typeParameters
        if (typeParams.isEmpty()) return null
        val simpleType = samType as? IrSimpleType ?: return null
        return IrTypeSubstitutor(
            typeParams.map { it.symbol },
            simpleType.arguments,
            allowEmptySubstitution = true,
        )
    }
}
