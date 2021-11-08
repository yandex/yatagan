package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

internal class VariantImpl private constructor(
    override val parts: Map<Variant.DimensionModel, Variant.FlavorModel>,
) : Variant {
    constructor(flavors: Sequence<TypeLangModel>)
            : this(flavors.map { FlavorImpl(it) }.associateBy(FlavorImpl::dimension))

    override fun plus(variant: Variant?): Variant {
        variant ?: return this
        return VariantImpl(buildMap {
            putAll(parts)
            variant.parts.forEach { (dimension, flavor) ->
                check(dimension !in this) {
                    "Duplicate dimension during variant concatenation: $dimension"
                }
                put(dimension, flavor)
            }
        })
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