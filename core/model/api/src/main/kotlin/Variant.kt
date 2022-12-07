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