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

plugins {
    base
    alias(libs.plugins.kotlua.gradlePlugin)
}

val commonMainFullSourcesConfig by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val commonNonJvmMainFullSourcesConfig by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val luaMainFullSourcesConfig by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)

    commonMainFullSourcesConfig(project(":lua-stdlib", configuration = "commonMainFullSourcesElements"))
    commonNonJvmMainFullSourcesConfig(project(":lua-stdlib", configuration = "commonNonJvmMainFullSourcesElements"))
    luaMainFullSourcesConfig(project(":lua-stdlib", configuration = "luaMainFullSourcesElements"))
}

val commonMainSources = tasks.register<Sync>("commonMainSources") {
    from(commonMainFullSourcesConfig) {
        exclude(
            "generated/_Arrays.kt",
            "generated/_Collections.kt",
            "generated/_Comparisons.kt",
            "generated/_Maps.kt",
            "generated/_OneToManyTitlecaseMappings.kt",
            "generated/_Sequences.kt",
            "generated/_Sets.kt",
            "generated/_Ranges.kt",
            "generated/_Strings.kt",
            "generated/_UArrays.kt",
            "generated/_URanges.kt",
            "generated/_UCollections.kt",
            "generated/_UComparisons.kt",
            "generated/_USequences.kt",
            "kotlin/AutoCloseable.kt",
            "kotlin/CharCode.kt",
            "kotlin/KotlinH.kt",
            "kotlin/MathH.kt",
            "kotlin/SequencesH.kt",
            "kotlin/TextH.kt",
            "kotlin/UnsignedCommon.kt",
            "kotlin/UProgressionUtil.kt",
            "kotlin/UByte.kt",
            "kotlin/UByteArray.kt",
            "kotlin/UByteRange.kt",
            "kotlin/UInt.kt",
            "kotlin/UIntArray.kt",
            "kotlin/UIntRange.kt",
            "kotlin/ULong.kt",
            "kotlin/ULongArray.kt",
            "kotlin/ULongRange.kt",
            "kotlin/UShort.kt",
            "kotlin/UShortArray.kt",
            "kotlin/UShortRange.kt",
            "kotlin/UMath.kt",
            "kotlin/UNumbers.kt",
            "kotlin/UStrings.kt",
            "kotlin/collections/**",
            "kotlin/concurrent/**",
            "kotlin/enums/**",
            "kotlin/io/**",
            "kotlin/properties/Delegates.kt",
            "kotlin/random/**",
            "kotlin/reflect/findAssociatedObject.kt",
            "kotlin/text/**",
            "kotlin/time/**",
            "kotlin/util/KotlinVersion.kt",
            "kotlin/util/Numbers.kt",
            "kotlin/util/FloorDivMod.kt",
            "kotlin/util/Tuples.kt",
            "kotlin/uuid/**",
        )
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commonMainSources"))
}

val commonMainCollectionSources = tasks.register<Sync>("commonMainCollectionSources") {
    from(commonMainFullSourcesConfig) {
        include(
            "kotlin/collections/PrimitiveIterators.kt",
        )
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commonMainCollectionSources"))
}

val commonNonJvmMainSources = tasks.register<Sync>("commonNonJvmMainSources") {
    from(commonNonJvmMainFullSourcesConfig) {
        exclude(
            "kotlin/reflect/KTypeImpl.kt",
            "kotlin/text/**",
        )
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commonNonJvmMainSources"))
}

val luaMainSources = tasks.register<Sync>("luaMainSources") {
    from(luaMainFullSourcesConfig) {
        exclude(
            "src/generated/**",
            "src/kotlin/text/**",
            "src/kotlin/collections/ArraysLua.kt"
        )
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("luaMainSources"))
}

kotlua {
    multiPlatformEnabled = true
    compilerOptions {
        nopack = false
        moduleName = "kotlin-stdlib-lua"
        freeCompilerArgs.addAll(
            listOf(
                "-Xallow-kotlin-package",
                "-Xstdlib-compilation",
                "-Xreturn-value-checker=full",
                "-Xexpect-actual-classes",
                "-Xname-based-destructuring=complete",
            ),
        )
        optIn = listOf(
            "kotlin.ExperimentalMultiplatform",
            "kotlin.contracts.ExperimentalContracts",
            "kotlin.ExperimentalStdlibApi",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.time.ExperimentalTime",
            "kotlin.io.encoding.ExperimentalEncodingApi",
        )
    }
}

sourceSets {
    commonMain {
        kotlin.setSrcDirs(emptyList<Any>())
        kotlin.srcDir(commonMainSources)
        kotlin.srcDir(commonMainCollectionSources)
        kotlin.srcDir("common")
    }
    luaMain {
        kotlin.setSrcDirs(emptyList<Any>())
        kotlin.srcDir(commonNonJvmMainSources)
        kotlin.srcDir(luaMainSources)
        kotlin.srcDir("src")
        kotlin.srcDir("builtins")
        kotlin.srcDir("runtime")
        resources.setSrcDirs(emptyList<Any>())
    }
}
