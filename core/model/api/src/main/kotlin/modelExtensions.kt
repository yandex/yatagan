@file:Suppress("NOTHING_TO_INLINE")

package com.yandex.yatagan.core.model

import com.yandex.yatagan.core.model.ComponentFactoryModel.InputModel
import com.yandex.yatagan.core.model.DependencyKind.Direct
import com.yandex.yatagan.core.model.DependencyKind.Lazy
import com.yandex.yatagan.core.model.DependencyKind.Optional
import com.yandex.yatagan.core.model.DependencyKind.OptionalLazy
import com.yandex.yatagan.core.model.DependencyKind.OptionalProvider
import com.yandex.yatagan.core.model.DependencyKind.Provider

val ComponentFactoryModel.allInputs: Sequence<InputModel>
    get() = factoryInputs.asSequence() + builderInputs.asSequence()

val DependencyKind.isOptional
    get() = when (this) {
        Direct, Lazy, Provider -> false
        Optional, OptionalLazy, OptionalProvider -> true
    }

val DependencyKind.isEager
    get() = when (this) {
        Direct, Optional -> true
        Lazy, Provider, OptionalLazy, OptionalProvider -> false
    }

inline fun <R> HasNodeModel?.accept(visitor: HasNodeModel.Visitor<R>): R {
    return if (this == null) {
        visitor.visitDefault()
    } else {
        accept(visitor)
    }
}

inline operator fun NodeDependency.component1(): NodeModel = node

inline operator fun NodeDependency.component2(): DependencyKind = kind


val ConditionScope.isAlways: Boolean
    get() = this == ConditionScope.Unscoped

val ConditionScope.isNever: Boolean
    get() = this == ConditionScope.NeverScoped
