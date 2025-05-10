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
    accessDelegate: BindingAccessDelegate,
): SynchronizedCachingAccessStrategy(binding, accessDelegate), dagger.Lazy<Any>

internal class CachingAccessStrategyDaggerCompat(
    binding: Binding,
    accessDelegate: BindingAccessDelegate,
) : CachingAccessStrategy(binding, accessDelegate), dagger.Lazy<Any>

internal class SynchronizedCreatingAccessStrategyDaggerCompat(
    binding: Binding,
    accessDelegate: BindingAccessDelegate,
) : SynchronizedCreatingAccessStrategy(binding, accessDelegate) {
    override fun getLazy(): Lazy<*> = SynchronizedCachingAccessStrategyDaggerCompat(
        binding = binding,
        accessDelegate = accessDelegate,
    )
}

internal class CreatingAccessStrategyDaggerCompat(
    binding: Binding,
    accessDelegate: BindingAccessDelegate,
) : CreatingAccessStrategy(binding, accessDelegate) {
    override fun getLazy(): Lazy<*> = CachingAccessStrategyDaggerCompat(
        binding = binding,
        accessDelegate = accessDelegate,
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
