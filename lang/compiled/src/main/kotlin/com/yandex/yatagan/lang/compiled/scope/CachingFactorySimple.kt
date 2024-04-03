package com.yandex.yatagan.lang.compiled.scope

import com.yandex.yatagan.lang.scope.CachingMetaFactory
import com.yandex.yatagan.lang.scope.LexicalScope

class CachingFactorySimple<T, R> internal constructor(
    private val delegate: LexicalScope.(T) -> R,
) : (LexicalScope, T) -> R {
    private val cache = hashMapOf<T, R>()

    override fun invoke(lexicalScope: LexicalScope, input: T): R = synchronized(cache) {
        return cache.getOrPut(input) {
            with(delegate) { invoke(lexicalScope, input) }
        }
    }

    companion object : CachingMetaFactory {
        override fun <T, R> createFactory(delegate: LexicalScope.(T) -> R) = CachingFactorySimple(delegate)
    }
}
