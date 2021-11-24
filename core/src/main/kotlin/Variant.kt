package com.yandex.daggerlite.core

/**
 * TODO: doc.
 */
interface Variant {
    interface DimensionModel : ClassBackedModel

    interface FlavorModel : ClassBackedModel {
        val dimension: DimensionModel
    }

    operator fun get(dimension: DimensionModel): FlavorModel?

    operator fun plus(variant: Variant?): Variant

    fun asMap(): Map<DimensionModel, FlavorModel>
}