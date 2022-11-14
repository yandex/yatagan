package com.yandex.yatagan.core.model

import com.yandex.yatagan.validation.MayBeInvalid

/**
 * TODO: doc.
 */
public interface Variant : MayBeInvalid {
    public interface DimensionModel : ClassBackedModel, MayBeInvalid {
        public val isInvalid: Boolean
    }

    public interface FlavorModel : ClassBackedModel, MayBeInvalid {
        public val dimension: DimensionModel
    }

    public operator fun get(dimension: DimensionModel): FlavorModel?

    public operator fun plus(variant: Variant?): Variant

    public fun asMap(): Map<DimensionModel, FlavorModel>
}