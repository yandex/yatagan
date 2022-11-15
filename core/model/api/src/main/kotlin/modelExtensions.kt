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


public val ConditionScope.isAlways: Boolean
    get() = this == ConditionScope.Unscoped

public val ConditionScope.isNever: Boolean
    get() = this == ConditionScope.NeverScoped
