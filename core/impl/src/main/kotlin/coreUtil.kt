package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.Direct
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.Lazy
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.Optional
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.OptionalLazy
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.OptionalProvider
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind.Provider

internal val NodeModel.Dependency.Kind.isOptional
    get() = when (this) {
        Direct, Lazy, Provider -> false
        Optional, OptionalLazy, OptionalProvider -> true
    }

internal val NodeModel.Dependency.Kind.isEager
    get() = when (this) {
        Direct, Optional -> true
        Lazy, Provider, OptionalLazy, OptionalProvider -> false
    }
