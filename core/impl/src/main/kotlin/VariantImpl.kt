package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class VariantImpl : Variant {

    constructor(flavors: Sequence<TypeLangModel>) {
        parts = flavors.map { FlavorImpl(it) }.associateBy(FlavorImpl::dimension)
    }

    private constructor(parts: Map<Variant.DimensionModel, Variant.FlavorModel>) {
        this.parts = parts
    }
    override val parts: Map<Variant.DimensionModel, Variant.FlavorModel>

    override fun plus(variant: Variant?): Variant {
        return VariantImpl(
            if (variant != null) this@VariantImpl.parts + variant.parts
            else this@VariantImpl.parts
        )
    }

    override fun toString() = "Variant[$parts]"

    private class DimensionImpl private constructor(
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

    private class FlavorImpl private constructor(
        override val type: TypeLangModel,
    ) : Variant.FlavorModel {
        init {
            require(type.declaration.isAnnotatedWith<ComponentFlavor>())
        }

        override val dimension: DimensionImpl =
            DimensionImpl(checkNotNull(type.declaration.componentFlavorIfPresent).dimension)

        override fun toString() = "Flavor[$type]"

        companion object Factory : ObjectCache<TypeLangModel, FlavorImpl>() {
            operator fun invoke(type: TypeLangModel) = createCached(type, ::FlavorImpl)
        }
    }
}