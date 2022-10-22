package com.yandex.daggerlite.core

import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * TODO: doc.
 */
interface Variant : MayBeInvalid {
    interface DimensionModel : ClassBackedModel, MayBeInvalid {
        val isInvalid: Boolean
    }

    interface FlavorModel : ClassBackedModel, MayBeInvalid {
        val dimension: DimensionModel
    }

    operator fun get(dimension: DimensionModel): FlavorModel?

    operator fun plus(variant: Variant?): Variant

    fun asMap(): Map<DimensionModel, FlavorModel>
}