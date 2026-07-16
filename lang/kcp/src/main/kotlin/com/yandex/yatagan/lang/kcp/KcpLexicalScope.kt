/*
 * Copyright 2026 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.LangOptions
import com.yandex.yatagan.lang.common.scope.LexicalScopeBase
import com.yandex.yatagan.lang.compiled.scope.CachingFactorySimple
import com.yandex.yatagan.lang.scope.CachingMetaFactory
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType

/**
 * Main entry point into IR-backed language models.
 *
 * [sourceFile] is used for compiler lookups so incremental compilation can associate every lookup with its source.
 */
public class KcpLexicalScope(
    internal val pluginContext: IrPluginContext,
    internal val sourceFile: IrFile,
) : LexicalScopeBase() {
    internal val finder: DeclarationFinder = pluginContext.finderForSource(sourceFile)

    /**
     * Every class the lang model wrapped while this scope was in use — the graph closure of the
     * components processed with it. Consumed by the processor to record incremental-compilation
     * lookups from [sourceFile] to these classes.
     */
    public val resolvedClasses: Set<IrClass> get() = resolvedClassesMutable
    internal val resolvedClassesMutable: MutableSet<IrClass> = LinkedHashSet()

    init {
        ext[KcpScopeKey] = this
        ext[CachingMetaFactory] = CachingFactorySimple
        ext[LangOptions] = LangOptions(daggerCompatibilityMode = false)
        ext[LangModelFactory] = KcpLangModelFactoryImpl(this)
    }

    public fun getType(type: IrType): Type = kcpType(type)

    public fun getTypeDeclaration(declaration: IrClass): TypeDeclaration {
        return kcpType(declaration.symbol.defaultType).declaration
    }
}

internal object KcpScopeKey : Extensible.Key<KcpLexicalScope, com.yandex.yatagan.lang.scope.LexicalScope.Extensions>
