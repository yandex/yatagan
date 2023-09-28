/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.model

import com.yandex.yatagan.base.api.Incubating
import com.yandex.yatagan.base.api.NullIfInvalid
import com.yandex.yatagan.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel.FeatureModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents an entity which is decorated with one or multiple [ConditionalWithFlavorConstraintsModel].
 * Such entities are conditionally included into a graph, based on runtime conditions (See [FeatureModel]) and
 * variant-based resolution (See [Variant]).
 */
public interface ConditionalHoldingModel : MayBeInvalid {
    @Incubating
    public val conditionals: List<ConditionalWithFlavorConstraintsModel>

    /**
     * Represents a "feature" - a named [ConditionScope].
     */
    @Incubating
    public interface FeatureModel : ClassBackedModel {
        /**
         * Parsed condition scope for this feature.
         */
        @NullIfInvalid
        public val conditionScope: ConditionScope?
    }

    /**
     * Model of a [com.yandex.yatagan.lang.BuiltinAnnotation.Conditional].
     */
    @Incubating
    public interface ConditionalWithFlavorConstraintsModel : MayBeInvalid {
        /**
         * list of features.
         */
        public val featureTypes: List<FeatureModel>

        /**
         * Complete [ConditionScope] for the conditional.
         */
        public val conditionScope: ConditionScope

        /**
         * Variant filter. May specify multiple flavors for a dimension.
         *
         * @see Variant
         */
        public val onlyIn: List<Variant.FlavorModel>
    }
}