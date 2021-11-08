package com.yandex.daggerlite.core

val ComponentFactoryModel.allInputs get() = factoryInputs.asSequence() + builderInputs.asSequence()

val DependencyKind.isOptional
    get() = when (this) {
        DependencyKind.Direct, DependencyKind.Lazy, DependencyKind.Provider -> false
        DependencyKind.Optional, DependencyKind.OptionalLazy, DependencyKind.OptionalProvider -> true
    }

val DependencyKind.isEager
    get() = when (this) {
        DependencyKind.Direct, DependencyKind.Optional -> true
        DependencyKind.Lazy, DependencyKind.Provider, DependencyKind.OptionalLazy, DependencyKind.OptionalProvider -> false
    }