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

@file:Suppress("NOTHING_TO_INLINE")

package com.yandex.yatagan.core.model

import com.yandex.yatagan.core.model.ComponentFactoryModel.InputModel
import com.yandex.yatagan.core.model.DependencyKind.Direct
import com.yandex.yatagan.core.model.DependencyKind.Lazy
import com.yandex.yatagan.core.model.DependencyKind.Optional
import com.yandex.yatagan.core.model.DependencyKind.OptionalLazy
import com.yandex.yatagan.core.model.DependencyKind.OptionalProvider
import com.yandex.yatagan.core.model.DependencyKind.Provider

public val ComponentFactoryModel.allInputs: Sequence<InputModel>
    get() = this.accept(object : ComponentFactoryModel.Visitor<Sequence<InputModel>> {
        override fun visitSubComponentFactoryMethod(model: SubComponentFactoryMethodModel) = model.factoryInputs.asSequence()
        override fun visitWithBuilder(model: ComponentFactoryWithBuilderModel) = model.allInputs
    })

public val ComponentFactoryWithBuilderModel.allInputs: Sequence<InputModel>
    get() = factoryInputs.asSequence() + builderInputs.asSequence()

public val DependencyKind.isOptional: Boolean
    get() = when (this) {
        Direct, Lazy, Provider -> false
        Optional, OptionalLazy, OptionalProvider -> true
    }

public val DependencyKind.isEager: Boolean
    get() = when (this) {
        Direct, Optional -> true
        Lazy, Provider, OptionalLazy, OptionalProvider -> false
    }

public inline fun <R> HasNodeModel?.accept(visitor: HasNodeModel.Visitor<R>): R {
    return if (this == null) {
        visitor.visitDefault()
    } else {
        accept(visitor)
    }
}

public inline operator fun NodeDependency.component1(): NodeModel = node

public inline operator fun NodeDependency.component2(): DependencyKind = kind

public inline infix fun ConditionScope.notImplies(another: ConditionScope): Boolean = !implies(another)