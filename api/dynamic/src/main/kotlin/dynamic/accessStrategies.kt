package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.Lazy
import com.yandex.daggerlite.Optional
import com.yandex.daggerlite.ThreadAssertions
import com.yandex.daggerlite.core.graph.Binding
import com.yandex.daggerlite.core.model.ConditionScope
import javax.inject.Provider


internal abstract class AccessStrategy : Provider<Any> {
    fun getProvider(): Provider<*> = this
    abstract fun getLazy(): Lazy<*>
    open fun getOptional(): Optional<*> = Optional.of(get())
    open fun getOptionalLazy(): Optional<Lazy<*>> = Optional.of(getLazy())
    open fun getOptionalProvider(): Optional<Provider<*>> = Optional.of(this)
}

internal class SynchronizedCachingAccessStrategy(
    private val binding: Binding,
    private val creationVisitor: Binding.Visitor<Any>,
) : AccessStrategy(), Lazy<Any> {
    @Volatile
    private var value: Any? = null

    override fun get(): Any {
        value?.let { local -> return local }
        return synchronized(this) {
            value?.let { local -> return local }
            binding.accept(creationVisitor).also { value = it }
        }
    }
    override fun getLazy(): Lazy<*> = this
}

internal class CachingAccessStrategy(
    private val binding: Binding,
    private val creationVisitor: Binding.Visitor<Any>,
) : AccessStrategy(), Lazy<Any> {
    private var value: Any? = null

    override fun get(): Any {
        ThreadAssertions.assertThreadAccess()
        value?.let { local -> return local }
        return binding.accept(creationVisitor).also { value = it }
    }
    override fun getLazy(): Lazy<*> = this
}

internal class SynchronizedCreatingAccessStrategy(
    private val binding: Binding,
    private val creationVisitor: Binding.Visitor<Any>,
) : AccessStrategy() {
    override fun get(): Any = binding.accept(creationVisitor)
    override fun getLazy(): Lazy<*> = SynchronizedCachingAccessStrategy(
        binding = binding,
        creationVisitor = creationVisitor,
    )
}

internal class CreatingAccessStrategy(
    private val binding: Binding,
    private val creationVisitor: Binding.Visitor<Any>,
) : AccessStrategy() {
    override fun get(): Any = binding.accept(creationVisitor)
    override fun getLazy(): Lazy<*> = CachingAccessStrategy(
        binding = binding,
        creationVisitor = creationVisitor,
    )
}

internal class ConditionalAccessStrategy(
    private val underlying: AccessStrategy,
    private val conditionScopeHolder: Binding,
    private val evaluator: ScopeEvaluator,
) : AccessStrategy() {
    interface ScopeEvaluator {
        fun evaluateConditionScope(conditionScope: ConditionScope): Boolean
    }

    override fun get(): Any = underlying.get()
    override fun getLazy(): Lazy<*> = underlying.getLazy()
    
    override fun getOptionalLazy(): Optional<Lazy<*>> =
        if (evaluator.evaluateConditionScope(conditionScopeHolder.conditionScope)) {
            underlying.getOptionalLazy()
        } else Optional.empty()

    override fun getOptional(): Optional<Any> =
        if (evaluator.evaluateConditionScope(conditionScopeHolder.conditionScope)) {
            underlying.getOptional()
        } else Optional.empty()

    override fun getOptionalProvider(): Optional<Provider<*>> =
        if (evaluator.evaluateConditionScope(conditionScopeHolder.conditionScope)) {
            underlying.getOptionalProvider()
        } else Optional.empty()
}


