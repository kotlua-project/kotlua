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

import org.jetbrains.kotlin.backend.common.lower.DefaultParameterCleaner
import org.jetbrains.kotlin.backend.common.lower.EnumWhenLowering
import org.jetbrains.kotlin.backend.common.lower.ExpressionBodyTransformer
import org.jetbrains.kotlin.backend.common.lower.InitializersLowering
import org.jetbrains.kotlin.backend.common.lower.InnerClassesLowering
import org.jetbrains.kotlin.backend.common.lower.LateinitLowering
import org.jetbrains.kotlin.backend.common.lower.LocalDeclarationsLowering
import org.jetbrains.kotlin.backend.common.lower.LocalDelegatedPropertiesLowering
import org.jetbrains.kotlin.backend.common.lower.PropertiesLowering
import org.jetbrains.kotlin.backend.common.lower.RangeContainsLowering
import org.jetbrains.kotlin.backend.common.lower.RedundantCastsRemoverLowering
import org.jetbrains.kotlin.backend.common.lower.SharedVariablesLowering
import org.jetbrains.kotlin.backend.common.lower.TailrecLowering
import org.jetbrains.kotlin.backend.common.lower.inline.InlineCallCycleCheckerLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineLambdasLowering
import org.jetbrains.kotlin.backend.common.lower.loops.ForLoopsLowering
import org.jetbrains.kotlin.backend.common.phaser.createModulePhases
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.phaser.NamedCompilerPhase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.inline.loweringsOfTheFirstPhase

// TODO
val luaLowerings: List<NamedCompilerPhase<LuaIrBackendContext, IrModuleFragment, IrModuleFragment>> =
    createModulePhases(
        ::InlineCallCycleCheckerLowering,
        ::LocalClassesInInlineLambdasLowering,
        ::RedundantCastsRemoverLowering,
        ::SharedVariablesLowering,
        ::LocalDeclarationsLowering,
        ::LocalDelegatedPropertiesLowering,
        ::TailrecLowering,
        ::InnerClassesLowering,
        ::PropertiesLowering,
        ::InitializersLowering,
        ::EnumWhenLowering,
        ::DefaultParameterCleaner,
        ::ForLoopsLowering,
        ::RangeContainsLowering,
        ::LateinitLowering,
        ::ExpressionBodyTransformer,
    )

fun luaLoweringsOfTheFirstPhase(
    languageVersionSettings: LanguageVersionSettings,
): List<NamedCompilerPhase<LuaPreSerializationLoweringContext, IrModuleFragment, IrModuleFragment>> =
    createModulePhases(*loweringsOfTheFirstPhase(languageVersionSettings).toTypedArray())
