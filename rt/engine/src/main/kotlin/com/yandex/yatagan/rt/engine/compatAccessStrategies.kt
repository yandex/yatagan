/*
 * Copyright 2024 Yandex LLC
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
import com.yandex.yatagan.core.graph.bindings.Binding

// WARNING: Do not use anything from the file, if `isDaggerCompat()` is not `true`!

internal class SynchronizedCachingAccessStrategyDaggerCompat(
    binding: Binding,
    creationVisitor: Binding.Visitor<Any>,
): SynchronizedCachingAccessStrategy(binding, creationVisitor), dagger.Lazy<Any>

internal class CachingAccessStrategyDaggerCompat(
    binding: Binding,
    creationVisitor: Binding.Visitor<Any>,
) : CachingAccessStrategy(binding, creationVisitor), dagger.Lazy<Any>

internal class SynchronizedCreatingAccessStrategyDaggerCompat(
    binding: Binding,
    creationVisitor: Binding.Visitor<Any>,
) : SynchronizedCreatingAccessStrategy(binding, creationVisitor) {
    override fun getLazy(): Lazy<*> = SynchronizedCachingAccessStrategyDaggerCompat(
        binding = binding,
        creationVisitor = creationVisitor,
    )
}

internal class CreatingAccessStrategyDaggerCompat(
    binding: Binding,
    creationVisitor: Binding.Visitor<Any>,
) : CreatingAccessStrategy(binding, creationVisitor) {
    override fun getLazy(): Lazy<*> = CachingAccessStrategyDaggerCompat(
        binding = binding,
        creationVisitor = creationVisitor,
    )
}

internal class StrategyFactoryDaggerCompat(
    component: RuntimeComponent
) : StrategyFactory(component) {
    override fun synchronizedCaching(binding: Binding): AccessStrategy {
        return SynchronizedCachingAccessStrategyDaggerCompat(binding, component)
    }
    override fun caching(binding: Binding): AccessStrategy {
        return CachingAccessStrategyDaggerCompat(binding, component)
    }
    override fun synchronizedCreating(binding: Binding): AccessStrategy {
        return SynchronizedCreatingAccessStrategyDaggerCompat(binding, component)
    }
    override fun creating(binding: Binding): AccessStrategy {
        return CreatingAccessStrategyDaggerCompat(binding, component)
    }
}