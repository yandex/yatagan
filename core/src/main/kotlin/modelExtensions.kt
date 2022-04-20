package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.ComponentFactoryModel.InputModel
import com.yandex.daggerlite.core.DependencyKind.Direct
import com.yandex.daggerlite.core.DependencyKind.Lazy
import com.yandex.daggerlite.core.DependencyKind.Optional
import com.yandex.daggerlite.core.DependencyKind.OptionalLazy
import com.yandex.daggerlite.core.DependencyKind.OptionalProvider
import com.yandex.daggerlite.core.DependencyKind.Provider

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

fun <R> HasNodeModel?.accept(visitor: HasNodeModel.Visitor<R>): R {
    return if (this == null) {
        visitor.visitDefault()
    } else {
        accept(visitor)
    }
}