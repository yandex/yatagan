package com.yandex.daggerlite.core

/**
 * TODO: doc.
 */
interface Variant {
    interface DimensionModel : ClassBackedModel

    interface FlavorModel : ClassBackedModel {
        val dimension: DimensionModel
    }

    val parts: Map<DimensionModel, FlavorModel>
}