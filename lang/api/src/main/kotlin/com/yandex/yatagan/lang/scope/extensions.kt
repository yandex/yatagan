package com.yandex.yatagan.lang.scope

import com.yandex.yatagan.base.api.Internal

/**
 * Creates a caching factory for [FactoryKey.factory].
 *
 * @param create a function to produce a result taking an [input][T].
 * @param T input type that is used as cache key.
 * @param R result type to cache.
 */
@Internal
public fun <T, R> LexicalScope.caching(create: (input: T) -> R): LexicalScope.(T) -> R {
    return ext[CachingMetaFactory].createFactory { create(it) }
}

/**
 * Creates a caching factory for [FactoryKey.factory].
 *
 * @param createWithScope a function to produce a result, taking a current [LexicalScope] and an [input][T].
 * @param T input type that is used as cache key.
 * @param R result type to cache.
 */
@Internal
public fun <T, R> LexicalScope.caching(createWithScope: (scope: LexicalScope, input: T) -> R): LexicalScope.(T) -> R {
    return ext[CachingMetaFactory].createFactory { createWithScope(this, it) }
}

/**
 * A method that is serves as [LexicalScope.invoke] in the absence of the immediate [LexicalScope] receiver.
 * This is possible due to [input type][T] a [LexicalScope] itself.
 *
 * @param T input type
 * @param R created instance type
 */
@Internal
public operator fun <T : LexicalScope, R> FactoryKey<T, R>.invoke(input: T): R {
    return with(input) { invoke(input) }
}
