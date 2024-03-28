package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.scope.LexicalScopeBase
import com.yandex.yatagan.lang.scope.CachingMetaFactory
import com.yandex.yatagan.lang.scope.LexicalScope
import java.lang.ref.SoftReference

/**
 * Main entry point into Reflection-backed lang.
 *
 * @param classLoader classLoader to use for loading new classes
 */
class RtLexicalScope(
    classLoader: ClassLoader,
) : LexicalScopeBase() {
    init {
        ext[CachingMetaFactory] = SoftReferenceCachingFactory
        ext[LangModelFactory] = RtModelFactoryImpl(
            lexicalScope = this,
            classLoader = classLoader,
        )
    }

    fun getTypeDeclaration(clazz: Class<*>): TypeDeclaration {
        return RtTypeImpl(clazz).declaration
    }

    /**
     * We use soft references in RT to potentially reduce memory consumption
     */
    private class SoftReferenceCachingFactory<T, R>(
        private val delegate: LexicalScope.(T) -> R,
    ) : (LexicalScope, T) -> R {
        private val cache = hashMapOf<T, SoftReference<R>>()

        override fun invoke(lexicalScope: LexicalScope, input: T): R = synchronized(cache) {
            return cache[input]?.get() ?: with(delegate) { invoke(lexicalScope, input) }.also {
                cache[input] = SoftReference(it)
            }
        }

        companion object : CachingMetaFactory {
            override fun <T, R> createFactory(delegate: LexicalScope.(T) -> R) = SoftReferenceCachingFactory(delegate)
        }
    }
}
