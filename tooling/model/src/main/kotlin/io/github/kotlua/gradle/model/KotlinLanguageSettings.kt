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
package io.github.kotlua.gradle.model

import java.io.File
import java.io.Serializable

// Copyright 2000-2022 JetBrains s.r.o. and contributors.
interface KotlinLanguageSettings : Serializable {
    val languageVersion: String?
    val apiVersion: String?
    val isProgressiveMode: Boolean
    val enabledLanguageFeatures: Set<String>

    @Deprecated("Unsupported and will be removed in next major releases", replaceWith = ReplaceWith("optInAnnotationsInUse"))
    val experimentalAnnotationsInUse: Set<String>
        get() = optInAnnotationsInUse

    val optInAnnotationsInUse: Set<String>
    val compilerPluginArguments: Array<String>
    val compilerPluginClasspath: Set<File>
    val freeCompilerArgs: Array<String>
}
