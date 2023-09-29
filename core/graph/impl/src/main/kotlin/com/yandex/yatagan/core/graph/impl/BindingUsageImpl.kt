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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.DependencyKind

internal class BindingUsageImpl : BindingGraph.BindingUsage {
    private var _direct: Int = 0
    private var _provider: Int = 0
    private var _lazy: Int = 0
    private var _optional: Int = 0
    private var _optionalLazy: Int = 0
    private var _optionalProvider: Int = 0

    override val direct get() = _direct + _optional
    override val provider get() = _provider + _optionalProvider
    override val lazy get() = _lazy + _optionalLazy
    override val optional get() = _optional + _optionalLazy + _optionalProvider
    override val optionalLazy get() = _optionalLazy
    override val optionalProvider get() = _optionalProvider

    fun accept(dependencyKind: DependencyKind) {
        when (dependencyKind) {
            DependencyKind.Direct -> _direct++
            DependencyKind.Lazy -> _lazy++
            DependencyKind.Provider -> _provider++
            DependencyKind.Optional -> _optional++
            DependencyKind.OptionalLazy -> _optionalLazy++
            DependencyKind.OptionalProvider -> _optionalProvider++
        }.let { /*exhaustive*/ }
    }
}