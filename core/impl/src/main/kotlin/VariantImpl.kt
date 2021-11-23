package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel

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
}