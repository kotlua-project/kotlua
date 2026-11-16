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

import java.io.Serializable

interface KotluaGradleModel : Serializable {
    val kotluaPluginVersion: String
    val sourceSetsByName: Map<String, KotluaSourceSet>
    val compilationsBySourceSetName: Map<String, KotluaCompilation>

    val sourceSets: List<KotluaSourceSet>
        get() = sourceSetsByName.values.toList()

    fun resolveAllDependsOn(sourceSet: KotluaSourceSet): Set<String> {
        val result = mutableSetOf<String>()

        fun collect(current: KotluaSourceSet) {
            for (depName in current.dependsOn) {
                if (result.add(depName)) {
                    sourceSetsByName[depName]?.let { collect(it) }
                }
            }
        }
        collect(sourceSet)
        return result
    }
}
