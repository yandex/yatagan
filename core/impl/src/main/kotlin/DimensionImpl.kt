package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class DimensionImpl private constructor(
    override val type: TypeLangModel,
) : Variant.DimensionModel {
    init {
        require(type.declaration.isAnnotatedWith<ComponentVariantDimension>())
    }

    override fun toString() = "Dimension[$type]"

    companion object Factory : ObjectCache<TypeLangModel, DimensionImpl>() {
        operator fun invoke(type: TypeLangModel) = DimensionImpl.createCached(type, ::DimensionImpl)
    }
}