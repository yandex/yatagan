package com.yandex.daggerlite.core

import com.yandex.daggerlite.BootstrapInterface
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

@JvmInline
value class BootstrapInterfaceModel(val impl: TypeLangModel) {
    init {
        require(impl.declaration.isAnnotatedWith<BootstrapInterface>())
    }
}
