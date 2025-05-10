/*
 * Copyright 2022 Yandex LLC
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

package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.Lazy
import com.yandex.yatagan.Optional
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.internal.YataganInternal
import javax.inject.Provider

internal abstract class AccessStrategy : Provider<Any> {
    fun getProvider(): Provider<*> = this
    abstract fun getLazy(): Lazy<*>
    open fun getOptional(): Optional<*> = Optional.of(get())
    open fun getOptionalLazy(): Optional<Lazy<*>> = Optional.of(getLazy())
    open fun getOptionalProvider(): Optional<Provider<*>> = Optional.of(this)
}

internal open class SynchronizedCachingAccessStrategy(
    private val binding: Binding,
    private val accessDelegate: BindingAccessDelegate,
) : AccessStrategy(), Lazy<Any> {
    @Volatile
    private var value: Any? = null

    final override fun get(): Any {
        value?.let { local -> return local }
        return synchronized(this) {
            value?.let { local -> return local }
            accessDelegate.createBinding(binding).also { value = it }
        }
    }
    final override fun getLazy(): Lazy<*> = this
}

internal open class CachingAccessStrategy(
    private val binding: Binding,
    private val accessDelegate: BindingAccessDelegate,
) : AccessStrategy(), Lazy<Any> {
    private var value: Any? = null

    @OptIn(YataganInternal::class)
    final override fun get(): Any {
        value?.let { local -> return local }
        accessDelegate.assertThreadAccessIfNeeded()
        return accessDelegate.createBinding(binding).also { value = it }
    }
    final override fun getLazy(): Lazy<*> = this
}

internal open class SynchronizedCreatingAccessStrategy(
    protected val binding: Binding,
    protected val accessDelegate: BindingAccessDelegate,
) : AccessStrategy() {
    final override fun get(): Any = accessDelegate.createBinding(binding)
    override fun getLazy(): Lazy<*> = SynchronizedCachingAccessStrategy(
        binding = binding,
        accessDelegate = accessDelegate,
    )
}

internal open class CreatingAccessStrategy(
    protected val binding: Binding,
    protected val accessDelegate: BindingAccessDelegate,
) : AccessStrategy() {
    final override fun get(): Any = accessDelegate.createBinding(binding)
    override fun getLazy(): Lazy<*> = CachingAccessStrategy(
        binding = binding,
        accessDelegate = accessDelegate,
    )
}

internal class ConditionalAccessStrategy(
    private val underlying: AccessStrategy,
    private val conditionScopeHolder: Binding,
    private val evaluator: RuntimeComponent,
) : AccessStrategy() {
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

internal open class StrategyFactory(
    protected val component: RuntimeComponent,
) {
    open fun synchronizedCaching(binding: Binding): AccessStrategy {
        return SynchronizedCachingAccessStrategy(binding, component)
    }

    open fun caching(binding: Binding): AccessStrategy {
        return CachingAccessStrategy(binding, component)
    }

    open fun synchronizedCreating(binding: Binding): AccessStrategy {
        return SynchronizedCreatingAccessStrategy(binding, component)
    }

    open fun creating(binding: Binding): AccessStrategy {
        return CreatingAccessStrategy(binding, component)
    }

    fun optional(underlying: AccessStrategy, binding: Binding) = ConditionalAccessStrategy(
        underlying = underlying,
        evaluator = component,
        conditionScopeHolder = binding,
    )
}