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

import io.github.kotlua.resolve.LuaDefaultImportsProvider
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.scopes.FirDefaultImportsProviderHolder
import org.jetbrains.kotlin.fir.scopes.impl.FirEnumEntriesSupport
import org.jetbrains.kotlin.fir.session.AbstractFirKlibSessionFactory
import org.jetbrains.kotlin.fir.session.FirSessionConfigurator

@OptIn(SessionConfiguration::class)
object FirLuaSessionFactory : AbstractFirKlibSessionFactory<FirLuaSessionFactory.Context>() {
    override fun createLibraryContext(configuration: CompilerConfiguration): Context = Context(configuration)

    override fun createSourceContext(configuration: CompilerConfiguration): Context = Context(configuration)

    override fun FirSession.registerLibrarySessionComponents(c: Context) {
        registerLuaComponents()
    }

    override fun FirSessionConfigurator.registerPlatformCheckers() = Unit

    override fun FirSessionConfigurator.registerExtraPlatformCheckers() = Unit

    override fun FirSession.registerSourceSessionComponents(c: Context) {
        registerLuaComponents()
    }

    private fun FirSession.registerLuaComponents() {
        register(FirEnumEntriesSupport(this))
        register(FirDefaultImportsProviderHolder.of(LuaDefaultImportsProvider))
    }

    class Context(
        val configuration: CompilerConfiguration,
    )
}
