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
package io.github.kotlua.ir

import io.github.kotlua.ir.lower.EnumClassConstructorBodyTransformer
import io.github.kotlua.ir.lower.EnumClassConstructorLowering
import io.github.kotlua.ir.lower.EnumClassRemoveEntriesLowering
import io.github.kotlua.ir.lower.EnumEntryCreateGetInstancesFunsLowering
import io.github.kotlua.ir.lower.EnumEntryInstancesBodyLowering
import io.github.kotlua.ir.lower.EnumEntryInstancesLowering
import io.github.kotlua.ir.lower.EnumSyntheticFunctionsAndPropertiesLowering
import io.github.kotlua.ir.lower.EnumUsageLowering
import io.github.kotlua.ir.lower.LuaBlockDecomposerLowering
import io.github.kotlua.ir.lower.LuaBridgeLowering
import io.github.kotlua.ir.lower.LuaCallableReferenceLowering
import io.github.kotlua.ir.lower.LuaCodeOutliningLowering
import io.github.kotlua.ir.lower.LuaLoopLowering
import io.github.kotlua.ir.lower.LuaMultiReturnLowering
import io.github.kotlua.ir.lower.LuaReturnableBlockLowering
import io.github.kotlua.ir.lower.LuaSingleAbstractMethodLowering
import io.github.kotlua.ir.lower.LuaStringConcatenationLowering
import io.github.kotlua.ir.lower.LuaTempVariableCleanupLowering
import io.github.kotlua.ir.lower.LuaTryCatchLowering
import io.github.kotlua.ir.lower.LuaTypeOperatorLowering
import io.github.kotlua.ir.lower.LuaVarargLowering
import io.github.kotlua.ir.lower.MainFunctionCallWrapperLowering
import io.github.kotlua.ir.lower.MultipleCatchesLowering
import io.github.kotlua.ir.lower.ObjectDeclarationLowering
import io.github.kotlua.ir.lower.ObjectUsageLowering
import io.github.kotlua.ir.lower.PrimitiveCompanionLowering
import io.github.kotlua.ir.lower.calls.CallsLowering
import io.github.kotlua.ir.lower.coroutines.LuaAddContinuationToFunctionCallsLowering
import io.github.kotlua.ir.lower.coroutines.LuaSuspendFunctionsLowering
import io.github.kotlua.ir.lower.inline.CopyInlineFunctionBodyLowering
import io.github.kotlua.ir.lower.inline.LuaAllFunctionInlining
import io.github.kotlua.ir.lower.inline.LuaPrivateFunctionInlining
import io.github.kotlua.ir.lower.inline.RemoveInlineDeclarationsWithReifiedTypeParametersLowering
import org.jetbrains.kotlin.backend.common.LoweringContext
import org.jetbrains.kotlin.backend.common.ModuleLoweringPass
import org.jetbrains.kotlin.backend.common.lower.ArrayConstructorLowering
import org.jetbrains.kotlin.backend.common.lower.DefaultArgumentStubGenerator
import org.jetbrains.kotlin.backend.common.lower.DefaultParameterCleaner
import org.jetbrains.kotlin.backend.common.lower.DefaultParameterInjector
import org.jetbrains.kotlin.backend.common.lower.EnumWhenLowering
import org.jetbrains.kotlin.backend.common.lower.ExpectDeclarationsRemoveLowering
import org.jetbrains.kotlin.backend.common.lower.ExpressionBodyTransformer
import org.jetbrains.kotlin.backend.common.lower.InitializersLowering
import org.jetbrains.kotlin.backend.common.lower.InnerClassConstructorCallsLowering
import org.jetbrains.kotlin.backend.common.lower.InnerClassesLowering
import org.jetbrains.kotlin.backend.common.lower.InnerClassesMemberBodyLowering
import org.jetbrains.kotlin.backend.common.lower.LateinitLowering
import org.jetbrains.kotlin.backend.common.lower.LocalDeclarationPopupLowering
import org.jetbrains.kotlin.backend.common.lower.LocalDeclarationsLowering
import org.jetbrains.kotlin.backend.common.lower.LocalDelegatedPropertiesLowering
import org.jetbrains.kotlin.backend.common.lower.MaskedDefaultArgumentFunctionFactory
import org.jetbrains.kotlin.backend.common.lower.PropertiesLowering
import org.jetbrains.kotlin.backend.common.lower.RangeContainsLowering
import org.jetbrains.kotlin.backend.common.lower.RedundantCastsRemoverLowering
import org.jetbrains.kotlin.backend.common.lower.SharedVariablesLowering
import org.jetbrains.kotlin.backend.common.lower.StripTypeAliasDeclarationsLowering
import org.jetbrains.kotlin.backend.common.lower.TailrecLowering
import org.jetbrains.kotlin.backend.common.lower.UpgradeCallableReferences
import org.jetbrains.kotlin.backend.common.lower.coroutines.AddContinuationToLocalSuspendFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.coroutines.AddContinuationToNonLocalSuspendFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.InlineCallCycleCheckerLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineLambdasLowering
import org.jetbrains.kotlin.backend.common.lower.loops.ForLoopsLowering
import org.jetbrains.kotlin.backend.common.phaser.createModulePhases
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.phaser.NamedCompilerPhase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.inline.OuterThisInInlineFunctionsSpecialAccessorLowering
import org.jetbrains.kotlin.ir.inline.SyntheticAccessorLowering
import org.jetbrains.kotlin.ir.inline.loweringsOfTheFirstPhase

private fun createUpgradeCallableReferences(context: LoweringContext): UpgradeCallableReferences =
    UpgradeCallableReferences(context, upgradeSamConversions = false)

private fun createLuaCodeOutliningPhaseOnFirstStage(context: LoweringContext): LuaCodeOutliningLowering = LuaCodeOutliningLowering(context)

private fun createLuaCodeOutliningPhaseOnSecondStage(context: LoweringContext): LuaCodeOutliningLowering = LuaCodeOutliningLowering(context)

private fun createSyntheticAccessorGenerationPhase(context: LoweringContext): SyntheticAccessorLowering = SyntheticAccessorLowering(context)

private fun createDefaultArgumentStubGenerator(context: LuaIrBackendContext) =
    DefaultArgumentStubGenerator(
        context = context,
        factory = MaskedDefaultArgumentFunctionFactory(context),
        skipInlineMethods = true,
        skipExternalMethods = true,
    )

private fun createDefaultParameterInjector(context: LuaIrBackendContext) =
    DefaultParameterInjector(
        context = context,
        factory = MaskedDefaultArgumentFunctionFactory(context),
        skipInline = true,
        skipExternalMethods = true,
    )

val luaLowerings: List<NamedCompilerPhase<LuaIrBackendContext, IrModuleFragment, IrModuleFragment>> =
    createModulePhases(
        ::InlineCallCycleCheckerLowering,
        ::createUpgradeCallableReferences,
        ::createLuaCodeOutliningPhaseOnSecondStage,
        ::LateinitLowering,
        ::SharedVariablesLowering,
        ::LocalClassesInInlineLambdasLowering,
        ::ArrayConstructorLowering,
        ::LuaPrivateFunctionInlining,
        ::OuterThisInInlineFunctionsSpecialAccessorLowering,
        ::createSyntheticAccessorGenerationPhase,
        ::LuaAllFunctionInlining,
        ::RedundantCastsRemoverLowering,
        ::CopyInlineFunctionBodyLowering,
        ::RemoveInlineDeclarationsWithReifiedTypeParametersLowering,
        ::ExpectDeclarationsRemoveLowering,
        ::StripTypeAliasDeclarationsLowering,
        ::TailrecLowering,
        ::LuaSingleAbstractMethodLowering,
        ::EnumClassConstructorLowering,
        ::EnumClassConstructorBodyTransformer,
        ::EnumEntryInstancesLowering,
        ::EnumEntryInstancesBodyLowering,
        ::EnumEntryCreateGetInstancesFunsLowering,
        ::LocalDeclarationsLowering,
        ::LocalDeclarationPopupLowering,
        ::createDefaultArgumentStubGenerator,
        ::createDefaultParameterInjector,
        ::DefaultParameterCleaner,
        ::LuaCallableReferenceLowering,
        ::LuaBridgeLowering,
        ::InnerClassesLowering,
        ::InnerClassesMemberBodyLowering,
        ::InnerClassConstructorCallsLowering,
        ::PropertiesLowering,
        ::InitializersLowering,
        ::EnumSyntheticFunctionsAndPropertiesLowering,
        ::ObjectDeclarationLowering,
        ::LuaStringConcatenationLowering,
        ::EnumWhenLowering,
        ::EnumUsageLowering,
        ::EnumClassRemoveEntriesLowering,
        ::LocalDelegatedPropertiesLowering,
        ::LuaSuspendFunctionsLowering,
        ::AddContinuationToNonLocalSuspendFunctionsLowering,
        ::AddContinuationToLocalSuspendFunctionsLowering,
        ::LuaAddContinuationToFunctionCallsLowering,
        ::LuaReturnableBlockLowering,
        ::RangeContainsLowering,
        ::ForLoopsLowering,
        ::PrimitiveCompanionLowering,
        ::ObjectUsageLowering,
        ::MultipleCatchesLowering,
        ::LuaTryCatchLowering,
        ::ExpressionBodyTransformer,
        ::LuaTypeOperatorLowering,
        ::LuaBlockDecomposerLowering,
        ::LuaVarargLowering,
        ::LuaLoopLowering,
        ::LuaMultiReturnLowering,
        ::MainFunctionCallWrapperLowering,
        ::CallsLowering,
        ::LuaTempVariableCleanupLowering,
    )

fun luaLoweringsOfTheFirstPhase(
    languageVersionSettings: LanguageVersionSettings,
): List<NamedCompilerPhase<LuaPreSerializationLoweringContext, IrModuleFragment, IrModuleFragment>> {
    val phases =
        buildList<(LuaPreSerializationLoweringContext) -> ModuleLoweringPass> {
            if (languageVersionSettings.supportsFeature(LanguageFeature.IrRichCallableReferencesInKlibs)) {
                this += ::createUpgradeCallableReferences
            }
            if (languageVersionSettings.supportsFeature(LanguageFeature.IrIntraModuleInlinerBeforeKlibSerialization)) {
                this += ::createLuaCodeOutliningPhaseOnFirstStage
            }
            this += loweringsOfTheFirstPhase(languageVersionSettings)
        }
    return createModulePhases(*phases.toTypedArray())
}
