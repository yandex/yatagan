package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.scope.LexicalScopeBase
import com.yandex.yatagan.lang.compiled.scope.CachingFactorySimple
import com.yandex.yatagan.lang.scope.CachingMetaFactory

/**
 * Main entry point into KSP-backed lang.
 *
 * @param resolver KSP resolver instance.
 */
class KspLexicalScope(
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
) : LexicalScopeBase() {
    init {
        check(environment.kspVersion >= KotlinVersion(2, 0)) {
            "KSP1 is not supported, use KSP2"
        }
        ext[CachingMetaFactory] = CachingFactorySimple
        ext[ProcessingUtils] = ProcessingUtils(resolver = resolver)
        ext[LangModelFactory] = KspModelFactoryImpl(this)
    }

    fun getTypeDeclaration(declaration: KSClassDeclaration): TypeDeclaration {
        return KspTypeImpl(declaration.asType(emptyList())).declaration
    }
}