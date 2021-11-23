package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class FlavorImpl private constructor(
    override val type: TypeLangModel,
) : Variant.FlavorModel {
    init {
        require(type.declaration.isAnnotatedWith<ComponentFlavor>())
    }

    override val dimension: Variant.DimensionModel =
        DimensionImpl(checkNotNull(type.declaration.componentFlavorIfPresent).dimension)

    override fun toString() = "Flavor[$type]"

    companion object Factory : ObjectCache<TypeLangModel, FlavorImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type, ::FlavorImpl)
    }
}