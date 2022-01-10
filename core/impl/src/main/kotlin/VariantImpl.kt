package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.Variant.DimensionModel
import com.yandex.daggerlite.core.Variant.FlavorModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal class VariantImpl private constructor(
    private val parts: Map<DimensionModel, List<FlavorModel>>,
) : Variant {
    constructor(flavors: Sequence<TypeLangModel>)
            : this(flavors.map { FlavorImpl(it) }.groupBy(FlavorImpl::dimension))

    override fun plus(variant: Variant?): Variant {
        variant ?: return this
        return VariantImpl(buildMap<DimensionModel, MutableList<FlavorModel>> {
            parts.forEach { (dimension, flavor) ->
                getOrPut(dimension, ::arrayListOf) += flavor
            }
            variant.asMap().forEach { (dimension, flavor) ->
                getOrPut(dimension, ::arrayListOf) += flavor
            }
        })
    }

    override fun get(dimension: DimensionModel): FlavorModel? {
        return parts[dimension]?.first()
    }

    override fun asMap(): Map<DimensionModel, FlavorModel> {
        return parts.mapValues { (_, flavors) -> flavors.first() }
    }

    override fun validate(validator: Validator) {
        parts.forEach { (dimension: DimensionModel, flavors: List<FlavorModel>) ->
            flavors.forEach(validator::child)
            if (flavors.size > 1) {
                validator.reportError(
                    Strings.Errors.`conflicting or duplicate flavors for dimension`(dimension = dimension)) {
                    for (conflict in flavors) {
                        addNote("Conflicting flavor: `$conflict`")
                    }
                }
            }
        }
    }

    override fun toString() = if (parts.isEmpty()) "Variant{empty}" else "Variant{...}"
}