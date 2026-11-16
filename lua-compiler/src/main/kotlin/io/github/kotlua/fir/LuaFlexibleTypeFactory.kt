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
package io.github.kotlua.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.deserialization.FirTypeDeserializer
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.metadata.ProtoBuf

class LuaFlexibleTypeFactory(
    private val session: FirSession,
) : FirTypeDeserializer.FlexibleTypeFactory {
    override fun createFlexibleType(
        proto: ProtoBuf.Type,
        lowerBound: ConeRigidType,
        upperBound: ConeRigidType,
    ): ConeFlexibleType = ConeFlexibleType(lowerBound, upperBound, isTrivial = false)

    override fun createDynamicType(
        proto: ProtoBuf.Type,
        lowerBound: ConeRigidType,
        upperBound: ConeRigidType,
    ): ConeDynamicType = ConeDynamicType.create(session, lowerBound.attributes)
}
