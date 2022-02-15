package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.Lazy
import com.yandex.daggerlite.Optional
import javax.inject.Provider


internal interface AccessStrategy {
    fun getDirect(): Any = getProvider().get()
    fun getProvider(): Provider<*> = getLazy()
    fun getLazy(): Lazy<*>
    fun getOptional(): Optional<*> = getOptionalProvider().map { it.get() }
    fun getOptionalLazy(): Optional<Lazy<*>> = Optional.of(getLazy())
    fun getOptionalProvider(): Optional<Provider<*>> = getOptionalLazy()
}

internal class CachingAccessStrategy(initializer: () -> Any) : AccessStrategy, Lazy<Any> {
    private val value by lazy(initializer)
    override fun get(): Any = value
    override fun getLazy(): Lazy<*> = this
}

internal class CreatingAccessStrategy(private val create: () -> Any) : AccessStrategy, Provider<Any> {
    override fun get(): Any = create()
    override fun getProvider(): Provider<*> = this
    override fun getLazy(): Lazy<*> = CachingAccessStrategy(initializer = this::get)
}

internal class ConditionalAccessStrategy(
    private val underlying: AccessStrategy,
    private val isPresent: () -> Boolean,
) : AccessStrategy {
    override fun getOptionalLazy(): Optional<Lazy<*>> {
        return if (isPresent()) underlying.getOptionalLazy() else Optional.empty()
    }

    override fun getLazy(): Lazy<*> = underlying.getLazy()
}


