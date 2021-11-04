package com.yandex.daggerlite.core

abstract class DimensionElementModel : ClassBackedModel() {
    abstract val dimension: DimensionModel
}

abstract class DimensionModel : ClassBackedModel()

interface VariantModel {
    val parts: Map<DimensionModel, DimensionElementModel>
}