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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.core.model.Variant.DimensionModel
import com.yandex.yatagan.core.model.Variant.FlavorModel
import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.buildError

internal sealed interface VariantMatch {
    val conditionScope: ConditionScope

    class Matched(override val conditionScope: ConditionScope) : VariantMatch
    class Error(val message: ValidationMessage?) : VariantMatch {
        override val conditionScope: ConditionScope
            get() = ConditionScope.Never
    }
}

private val MatchedUnscoped = VariantMatch.Matched(ConditionScope.Always)
private val MatchedNeverScoped = VariantMatch.Matched(ConditionScope.Never)

internal fun VariantMatch(
    conditionalHoldingModel: ConditionalHoldingModel,
    forVariant: Variant,
): VariantMatch {
    if (conditionalHoldingModel.conditionals.none()) {
        return MatchedUnscoped
    }

    var maxMatched = -1
    var bestMatch: ConditionalWithFlavorConstraintsModel? = null
    outer@ for (conditional in conditionalHoldingModel.conditionals) {
        val constraints: Map<DimensionModel, List<FlavorModel>> = conditional.onlyIn
            .groupBy(FlavorModel::dimension)
        var currentMatch = 0
        for ((dimension: DimensionModel, allowedFlavors: List<FlavorModel>) in constraints) {
            val flavor = forVariant[dimension]
                ?: return if (dimension.isInvalid) {
                    // No need to report a missing dimension error here, as <error> dimension is, of course, missing
                    VariantMatch.Error(null)
                } else {
                    VariantMatch.Error(buildError(Strings.Errors.undeclaredDimension(
                        dimension = dimension,
                    )))
                }

            if (flavor !in allowedFlavors) {
                // Not matched
                continue@outer
            }
            ++currentMatch
        }
        if (maxMatched == currentMatch) {
            return VariantMatch.Error(
                buildError(Strings.Errors.variantMatchingAmbiguity(one = bestMatch!!, two = conditional)))
        }
        if (maxMatched < currentMatch) {
            maxMatched = currentMatch
            bestMatch = conditional
        }
    }
    return bestMatch?.conditionScope?.let(VariantMatch::Matched) ?: MatchedNeverScoped
}