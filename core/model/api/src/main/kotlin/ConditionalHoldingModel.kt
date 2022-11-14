package com.yandex.yatagan.core.model

import com.yandex.yatagan.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel.FeatureModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents an entity which is decorated with one or multiple [ConditionalWithFlavorConstraintsModel].
 * Such entities are conditionally included into a graph, based on runtime conditions (See [FeatureModel]) and
 * variant-based resolution (See [Variant]).
 */
public interface ConditionalHoldingModel : MayBeInvalid {
    public val conditionals: List<ConditionalWithFlavorConstraintsModel>

    /**
     * Represents a "feature" - a named [ConditionScope].
     */
    public interface FeatureModel : MayBeInvalid, ClassBackedModel {
        public val conditionScope: ConditionScope
    }

    /**
     * Model of a [com.yandex.yatagan.lang.BuiltinAnnotation.Conditional].
     */
    public interface ConditionalWithFlavorConstraintsModel : MayBeInvalid {
        public val featureTypes: List<FeatureModel>
        public val onlyIn: List<Variant.FlavorModel>
    }
}