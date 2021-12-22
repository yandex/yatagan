package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.FeatureModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents an entity which is decorated with one or multiple [ConditionalWithFlavorConstraintsModel].
 * Such entities are conditionally included into a graph, based on runtime conditions (See [FeatureModel]) and
 * variant-based resolution (See [Variant]).
 */
interface ConditionalHoldingModel : MayBeInvalid {
    val conditionals: Sequence<ConditionalWithFlavorConstraintsModel>

    /**
     * Represents a "feature". TODO: reference API doc.
     */
    interface FeatureModel : MayBeInvalid {
        val conditions: Sequence<ConditionLangModel>
    }

    /**
     * Core-level model of a [com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel].
     */
    interface ConditionalWithFlavorConstraintsModel {
        val featureTypes: Sequence<FeatureModel>
        val onlyIn: Sequence<Variant.FlavorModel>
    }
}