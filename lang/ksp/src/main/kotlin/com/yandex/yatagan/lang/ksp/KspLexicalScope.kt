package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.processing.Resolver
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
) : LexicalScopeBase() {
    init {
        ext[CachingMetaFactory] = CachingFactorySimple
        ext[ProcessingUtils] = ProcessingUtils(resolver)
        ext[LangModelFactory] = KspModelFactoryImpl(this)
    }

    fun getTypeDeclaration(declaration: KSClassDeclaration): TypeDeclaration {
        return KspTypeImpl(declaration.asType(emptyList())).declaration
    }
}