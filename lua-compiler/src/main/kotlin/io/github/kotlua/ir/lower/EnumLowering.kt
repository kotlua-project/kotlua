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
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PRIVATE
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom
import org.jetbrains.kotlin.utils.addToStdlib.getOrSetIfNull
import org.jetbrains.kotlin.utils.memoryOptimizedMap
import org.jetbrains.kotlin.utils.memoryOptimizedPlus

private val ENUM_GET_INSTANCE_FUNCTION by IrDeclarationOriginImpl.Regular

private var IrConstructor.newEnumConstructor: IrConstructor? by irAttribute(copyByDefault = false)
private var IrValueDeclaration.valueParameterForOldEnumConstructor: IrValueParameter? by irAttribute(copyByDefault = false)
private var IrClass.correspondingEnumEntry: IrEnumEntry? by irAttribute(copyByDefault = false)
private var IrEnumEntry.correspondingField: IrField? by irAttribute(copyByDefault = false)
private var IrEnumEntry.getInstanceFun: IrSimpleFunction? by irAttribute(copyByDefault = false)

private val IrClass.isInstantiableEnum: Boolean
    get() = isEnumClass && !isExpect && !isEffectivelyExternal()

private val IrDeclaration.parentEnumClassOrNull: IrClass?
    get() = parentClassOrNull?.takeIf { it.isEnumClass }

private val IrClass.enumEntries: List<IrEnumEntry>
    get() = declarations.filterIsInstance<IrEnumEntry>()

private fun IrEnumEntry.getType(irClass: IrClass) = (correspondingClass ?: irClass).defaultType

private fun createEntryAccessorName(enumEntry: IrEnumEntry) = "${enumEntry.name}\$getInstance"

class EnumClassConstructorLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    private val additionalParameters =
        listOf(
            "name" to context.irBuiltIns.stringType,
            "ordinal" to context.irBuiltIns.intType,
        )

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        val irClass = declaration.parent as? IrClass ?: return null
        if (!irClass.isInstantiableEnum) return null

        if (declaration is IrConstructor) {
            return listOf(transformEnumConstructor(declaration, irClass))
        }

        if (declaration is IrEnumEntry) {
            declaration.correspondingClass?.let { klass ->
                klass.correspondingEnumEntry = declaration
            }
        }

        return null
    }

    private fun transformEnumConstructor(
        enumConstructor: IrConstructor,
        enumClass: IrClass,
    ): IrConstructor =
        context.irFactory
            .buildConstructor {
                updateFrom(enumConstructor)
            }.apply {
                parent = enumClass
                for ((paramName, paramType) in additionalParameters) {
                    addValueParameter(paramName, paramType)
                }
                copyFunctionSignatureFrom(enumConstructor)

                val newConstructor = this
                enumConstructor.newEnumConstructor = this

                for (old in enumConstructor.parameters) {
                    val new = parameters[additionalParameters.size + old.indexInParameters]
                    old.valueParameterForOldEnumConstructor = new

                    old.defaultValue?.let { default ->
                        new.defaultValue =
                            context.irFactory
                                .createExpressionBody(
                                    startOffset = default.startOffset,
                                    endOffset = default.endOffset,
                                    expression = default.expression,
                                ).apply {
                                    expression.patchDeclarationParents(newConstructor)
                                    context.fixReferencesToConstructorParameters(enumClass, this)
                                }
                    }
                }

                enumConstructor.body?.let { oldBody ->
                    body =
                        context.irFactory.createBlockBody(oldBody.startOffset, oldBody.endOffset) {
                            statements += (oldBody as IrBlockBody).statements
                            context.fixReferencesToConstructorParameters(enumClass, this)
                            patchDeclarationParents(newConstructor)
                        }
                }
            }
}

private fun LuaIrBackendContext.fixReferencesToConstructorParameters(
    irClass: IrClass,
    body: IrBody,
) {
    body.transformChildrenVoid(
        object : IrElementTransformerVoid() {
            private val builder = createIrBuilder(irClass.symbol)

            override fun visitGetValue(expression: IrGetValue): IrExpression {
                expression.symbol.owner.valueParameterForOldEnumConstructor?.let {
                    return builder.irGet(it)
                }
                return super.visitGetValue(expression)
            }

            override fun visitSetValue(expression: IrSetValue): IrExpression {
                expression.transformChildrenVoid()
                return expression.symbol.owner.valueParameterForOldEnumConstructor?.let {
                    builder.irSet(it.symbol, expression.value)
                } ?: expression
            }
        },
    )
}

@PhasePrerequisites(EnumClassConstructorLowering::class)
class EnumClassConstructorBodyTransformer(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val irClass = container.parent as? IrClass ?: return

        context.fixReferencesToConstructorParameters(irClass, irBody)

        if (container is IrConstructor) {
            if (irClass.isInstantiableEnum) {
                lowerEnumConstructorsBody(container)
            }

            irClass.correspondingEnumEntry?.let { enumEntry ->
                lowerEnumEntryClassConstructors(enumEntry.parentAsClass, enumEntry, container)
            }
        }

        if (container is IrEnumEntry) {
            lowerEnumEntryInitializerExpression(irClass, container)
        }
    }

    private fun lowerEnumConstructorsBody(constructor: IrConstructor) {
        constructor.body?.transformChildrenVoid(IrEnumClassConstructorTransformer(constructor))
    }

    private inner class IrEnumClassConstructorTransformer(
        val constructor: IrConstructor,
    ) : IrElementTransformerVoid() {
        val builder = context.createIrBuilder(constructor.symbol)

        override fun visitEnumConstructorCall(expression: IrEnumConstructorCall): IrExpression =
            builder.irDelegatingConstructorCall(expression.symbol.owner).apply {
                for (i in 0..1) {
                    arguments[i] = builder.irGet(constructor.parameters[i])
                }
                typeArguments.assignFrom(expression.typeArguments)
            }

        override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
            val delegatingConstructor = expression.symbol.owner.let { it.newEnumConstructor ?: it }
            return builder.irDelegatingConstructorCall(delegatingConstructor).apply {
                var valueArgIdx = 0
                for (i in 0..1) {
                    arguments[valueArgIdx++] = builder.irGet(constructor.parameters[i])
                }
                for (argument in expression.arguments) {
                    arguments[valueArgIdx++] = argument
                }
                typeArguments.assignFrom(expression.typeArguments)
            }
        }
    }

    private fun lowerEnumEntryClassConstructors(
        irClass: IrClass,
        entry: IrEnumEntry,
        constructor: IrConstructor,
    ) {
        constructor.transformChildrenVoid(IrEnumEntryClassConstructorTransformer(irClass, entry, isInsideConstructor = true))
    }

    private fun lowerEnumEntryInitializerExpression(
        irClass: IrClass,
        entry: IrEnumEntry,
    ) {
        entry.initializerExpression =
            entry.initializerExpression?.transform(
                IrEnumEntryClassConstructorTransformer(irClass, entry, isInsideConstructor = false),
                null,
            )
    }

    private inner class IrEnumEntryClassConstructorTransformer(
        irClass: IrClass,
        val entry: IrEnumEntry,
        val isInsideConstructor: Boolean,
    ) : IrElementTransformerVoid() {
        private val enumEntries = irClass.enumEntries

        private fun buildConstructorCall(
            constructor: IrConstructor,
            constructorCall: IrEnumConstructorCall,
        ): IrFunctionAccessExpression =
            if (isInsideConstructor) {
                IrDelegatingConstructorCallImpl.fromSymbolOwner(
                    startOffset = constructorCall.startOffset,
                    endOffset = constructorCall.endOffset,
                    type = context.irBuiltIns.unitType,
                    symbol = constructor.symbol,
                )
            } else {
                IrConstructorCallImpl.fromSymbolOwner(
                    startOffset = constructorCall.startOffset,
                    endOffset = constructorCall.endOffset,
                    type = constructor.constructedClassType,
                    constructorSymbol = constructor.symbol,
                )
            }

        override fun visitEnumConstructorCall(expression: IrEnumConstructorCall): IrExpression {
            var constructor = expression.symbol.owner
            val constructorWasTransformed = constructor.newEnumConstructor != null
            if (constructorWasTransformed) {
                constructor = constructor.newEnumConstructor!!
            }

            val builder = context.createIrBuilder(entry.symbol, expression.startOffset, expression.endOffset)
            return buildConstructorCall(constructor, expression).apply {
                var valueArgIdx = 0
                if (constructorWasTransformed) {
                    arguments[valueArgIdx++] = builder.irString(entry.name.identifier)
                    arguments[valueArgIdx++] = builder.irInt(enumEntries.indexOf(entry))
                }
                for (argument in expression.arguments) {
                    arguments[valueArgIdx++] = argument
                }
                typeArguments.assignFrom(expression.typeArguments)
            }
        }
    }
}

@PhasePrerequisites(EnumClassConstructorLowering::class)
class EnumEntryInstancesLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration is IrEnumEntry) {
            val irClass = declaration.parentAsClass
            if (irClass.isInstantiableEnum) {
                return listOf(declaration, createEnumEntryInstanceVariable(irClass, declaration))
            }
        }
        return null
    }

    private fun createEnumEntryInstanceVariable(
        irClass: IrClass,
        enumEntry: IrEnumEntry,
    ): IrField {
        val field =
            context.irFactory
                .buildField {
                    name = Name.identifier("${enumEntry.name}\$INSTANCE")
                    type = enumEntry.getType(irClass).makeNullable()
                    origin = IrDeclarationOrigin.FIELD_FOR_ENUM_ENTRY
                    isStatic = true
                }.apply {
                    parent = irClass
                    initializer = null
                }

        enumEntry.correspondingField = field
        return field
    }
}

@PhasePrerequisites(EnumEntryInstancesLowering::class)
class EnumEntryInstancesBodyLowering(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        if (container is IrConstructor && container.constructedClass.kind == ClassKind.ENUM_ENTRY) {
            val entryClass = container.constructedClass
            val enum = entryClass.parentAsClass
            if (enum.isInstantiableEnum) {
                val entry =
                    enum.declarations.first { it is IrEnumEntry && it.correspondingClass === entryClass } as IrEnumEntry

                val blockBody = irBody as IrBlockBody
                val index =
                    blockBody.statements.indexOfFirst {
                        it is IrDelegatingConstructorCall ||
                            (it is IrTypeOperatorCall && it.argument is IrDelegatingConstructorCall)
                    } + 1

                blockBody.statements.add(
                    index,
                    context.createIrBuilder(container.symbol).run {
                        irSetField(null, entry.correspondingField!!, irGet(entryClass.thisReceiver!!))
                    },
                )
            }
        }
    }
}

@PhasePrerequisites(
    EnumClassConstructorLowering::class,
    EnumClassConstructorBodyTransformer::class,
    EnumEntryInstancesLowering::class,
)
class EnumEntryCreateGetInstancesFunsLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration is IrEnumEntry) {
            val irClass = declaration.parentAsClass
            if (irClass.isInstantiableEnum) {
                val entryGetInstanceFun = createGetEntryInstanceFun(irClass, declaration)
                return listOf(declaration, entryGetInstanceFun)
            }
        }
        return null
    }

    private fun createGetEntryInstanceFun(
        irClass: IrClass,
        enumEntry: IrEnumEntry,
    ): IrSimpleFunction {
        val entryType = enumEntry.getType(irClass)
        val field = enumEntry.correspondingField!!

        return enumEntry::getInstanceFun
            .getOrSetIfNull {
                context.irFactory
                    .buildFun {
                        name = Name.identifier(createEntryAccessorName(enumEntry))
                        returnType = entryType
                        origin = ENUM_GET_INSTANCE_FUNCTION
                    }.apply {
                        parent = irClass
                    }
            }.also { fn ->
                val initExpr =
                    enumEntry.initializerExpression?.expression?.also {
                        it.patchDeclarationParents(fn)
                        enumEntry.initializerExpression = null
                    }

                fn.body =
                    context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                        statements +=
                            context
                                .createIrBuilder(fn.symbol)
                                .irBlockBody(fn) {
                                    if (initExpr != null) {
                                        +irIfThen(
                                            irEqualsNull(irGetField(null, field)),
                                            irSetField(null, field, initExpr),
                                        )
                                    }
                                    +irReturn(
                                        IrTypeOperatorCallImpl(
                                            UNDEFINED_OFFSET,
                                            UNDEFINED_OFFSET,
                                            entryType,
                                            IrTypeOperator.REINTERPRET_CAST,
                                            entryType,
                                            irGetField(null, field),
                                        ),
                                    )
                                }.statements
                    }
            }
    }
}

private val ENTRIES_FIELD_NAME = Name.identifier("ENTRIES")

@PhasePrerequisites(
    EnumClassConstructorLowering::class,
    EnumEntryCreateGetInstancesFunsLowering::class,
)
class EnumSyntheticFunctionsAndPropertiesLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    private val throwIAESymbol = context.symbols.throwIllegalArgumentException

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration is IrSimpleFunction) {
            val syntheticBody = declaration.body as? IrSyntheticBody ?: return null
            val enumClass = declaration.parentEnumClassOrNull ?: return null
            if (!enumClass.isInstantiableEnum) return null

            declaration.body =
                context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                    statements +=
                        when (syntheticBody.kind) {
                            IrSyntheticBodyKind.ENUM_VALUES -> createEnumValuesBody(declaration, enumClass)
                            IrSyntheticBodyKind.ENUM_VALUEOF -> createEnumValueOfBody(declaration, enumClass)
                            IrSyntheticBodyKind.ENUM_ENTRIES -> createEnumEntriesBody(declaration, enumClass)
                        }.statements
                }
        }
        return null
    }

    private fun createEnumEntriesBody(
        entriesGetter: IrFunction,
        enumClass: IrClass,
    ): IrBlockBody {
        val enumEntriesSymbol = context.symbols.enumEntriesOrNull
        val createEnumEntriesFunction = context.symbols.createEnumEntriesOrNull
        if (enumEntriesSymbol == null || createEnumEntriesFunction == null) {
            return context.createIrBuilder(entriesGetter.symbol).run {
                irBlockBody {
                    +irCall(throwIAESymbol).apply {
                        arguments[0] = irString("Enum.entries is not supported: kotlin.enums.EnumEntries is missing in stdlib")
                    }
                }
            }
        }

        val entriesField = enumClass.buildEntriesField(enumEntriesSymbol)
        val valuesFunction = enumClass.searchForValuesFunction()
        val entriesType = enumEntriesSymbol.typeWith(enumClass.defaultType)

        return context.createIrBuilder(entriesGetter.symbol).run {
            irBlockBody {
                +irIfThen(
                    irEqualsNull(irGetField(null, entriesField)),
                    irSetField(
                        null,
                        entriesField,
                        irCall(createEnumEntriesFunction, entriesType).apply {
                            typeArguments[0] = enumClass.defaultType
                            arguments[0] = irCall(valuesFunction)
                        },
                    ),
                )
                +irReturn(irGetField(null, entriesField))
            }
        }
    }

    private fun IrClass.buildEntriesField(enumEntriesSymbol: IrClassSymbol): IrField =
        addField {
            name = ENTRIES_FIELD_NAME
            type = enumEntriesSymbol.typeWith(defaultType).makeNullable()
            visibility = PRIVATE
            origin = IrDeclarationOrigin.FIELD_FOR_ENUM_ENTRIES
            isFinal = true
            isStatic = true
        }

    private fun IrClass.searchForValuesFunction(): IrFunction =
        declarations.first {
            it is IrSimpleFunction &&
                it.dispatchReceiverParameter == null &&
                it.parameters.isEmpty() &&
                it.returnType.isArray()
        } as IrFunction

    private fun createEnumValueOfBody(
        valueOfFun: IrFunction,
        irClass: IrClass,
    ): IrBlockBody {
        val nameParameter = valueOfFun.parameters[0]

        return context.createIrBuilder(valueOfFun.symbol).run {
            irBlockBody {
                +irWhen(
                    irClass.defaultType,
                    irClass.enumEntries.memoryOptimizedMap {
                        irBranch(
                            irEquals(irGet(nameParameter), irString(it.name.identifier)),
                            irReturn(irCall(it.getInstanceFun!!)),
                        )
                    } memoryOptimizedPlus
                        irElseBranch(
                            irBlock {
                                +irCall(throwIAESymbol).apply {
                                    arguments[0] =
                                        irConcat().apply {
                                            addArgument(irString("No enum constant ${irClass.kotlinFqName}."))
                                            addArgument(irGet(nameParameter))
                                        }
                                }
                            },
                        ),
                )
            }
        }
    }

    private fun createEnumValuesBody(
        valuesFun: IrFunction,
        irClass: IrClass,
    ): IrBlockBody =
        context.createIrBuilder(valuesFun.symbol).run {
            irBlockBody { +irReturn(arrayOfEnumEntriesOf(irClass)) }
        }

    private fun IrBuilderWithScope.arrayOfEnumEntriesOf(enumClass: IrClass) =
        irVararg(enumClass.defaultType, enumClass.enumEntries.map { irCall(it.getInstanceFun!!) })
}

@PhasePrerequisites(EnumEntryCreateGetInstancesFunsLowering::class)
class EnumUsageLowering(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        irBody.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitGetEnumValue(expression: IrGetEnumValue): IrExpression {
                    val enumEntry = expression.symbol.owner
                    val klass = enumEntry.parent as IrClass
                    if (klass.isEffectivelyExternal()) return expression
                    return IrCallImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = expression.type,
                        symbol = enumEntry.getInstanceFun!!.symbol,
                        typeArgumentsCount = 0,
                    )
                }
            },
        )
    }
}

@PhasePrerequisites(EnumUsageLowering::class)
class EnumClassRemoveEntriesLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration is IrEnumEntry && !declaration.isExpect && !declaration.isEffectivelyExternal()) {
            return listOfNotNull(declaration.correspondingClass)
        }
        return null
    }
}
