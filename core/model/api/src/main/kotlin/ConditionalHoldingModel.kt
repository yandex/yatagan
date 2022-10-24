package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.model.ConditionalHoldingModel.FeatureModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents an entity which is decorated with one or multiple [ConditionalWithFlavorConstraintsModel].
 * Such entities are conditionally included into a graph, based on runtime conditions (See [FeatureModel]) and
 * variant-based resolution (See [Variant]).
 */
interface ConditionalHoldingModel : MayBeInvalid {
    val conditionals: List<ConditionalWithFlavorConstraintsModel>

    /**
     * Represents a "feature" - a named [ConditionScope].
     */
    interface FeatureModel : MayBeInvalid, ClassBackedModel {
        val conditionScope: ConditionScope
    }

    /**
     * Model of a [com.yandex.daggerlite.lang.BuiltinAnnotation.Conditional].
     */
    interface ConditionalWithFlavorConstraintsModel : MayBeInvalid {
        val featureTypes: List<FeatureModel>
        val onlyIn: List<Variant.FlavorModel>
    }
}