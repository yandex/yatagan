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
import com.yandex.yatagan.core.model.Variant.DimensionModel
import com.yandex.yatagan.core.model.Variant.FlavorModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Variant is a collection of [flavors][FlavorModel] (with the corresponding [dimensions][DimensionModel]).
 * A variant is valid when containing only one *flavor* per *dimension*.
 * A variant is a component's attribute and is used to filter bindings in the component according to their [variant
 * filters][ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel].
 *
 * @see ComponentModel.variant
 * @see ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
 */
@Incubating
public interface Variant : MayBeInvalid {
    /**
     * A declared named dimension model. Multiple [flavors][FlavorModel] may belong to a dimensions.
     */
    public interface DimensionModel : ClassBackedModel {
        /**
         * Whether this dimension is not valid (unresolved).
         */
        public val isInvalid: Boolean
    }

    /**
     * A declared named flavor model. Belong to a single [dimension].
     */
    public interface FlavorModel : ClassBackedModel {
        /**
         * The dimension the flavor belongs to.
         */
        public val dimension: DimensionModel
    }

    /**
     * Get a specific flavor by the dimension.
     *
     * @return a flavor or `null` if there's no flavor for the given dimension.
     */
    public operator fun get(dimension: DimensionModel): FlavorModel?

    /**
     * Merges this variant with the given, return the resulting variant.
     *
     * @return resulting variant, which may end up invalid if contains multiple flavors for a single dimensions.
     */
    public operator fun plus(variant: Variant?): Variant

    /**
     * Presents the variant as a [Map].
     *
     * @return a map {dimension: flavor}.
     */
    public fun asMap(): Map<DimensionModel, FlavorModel>
}