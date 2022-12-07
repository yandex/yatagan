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
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.internal.ThreadAssertions
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


