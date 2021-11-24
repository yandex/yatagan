package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel

internal class VariantImpl private constructor(
    private val parts: Map<Variant.DimensionModel, Variant.FlavorModel>,
) : Variant {
    constructor(flavors: Sequence<TypeLangModel>)
            : this(flavors.map { FlavorImpl(it) }.associateBy(FlavorImpl::dimension))

    override fun plus(variant: Variant?): Variant {
        variant ?: return this
        return VariantImpl(buildMap {
            putAll(parts)
            variant.asMap().forEach { (dimension, flavor) ->
                check(dimension !in this) {
                    "Duplicate dimension during variant concatenation: $dimension"
                }
                put(dimension, flavor)
            }
        })
    }

    override fun get(dimension: Variant.DimensionModel): Variant.FlavorModel? {
        return parts[dimension]
    }

    override fun asMap(): Map<Variant.DimensionModel, Variant.FlavorModel> {
        return parts
    }

    override fun toString() = "Variant[$parts]"
}