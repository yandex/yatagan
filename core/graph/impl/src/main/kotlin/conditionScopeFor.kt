package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.ConditionalHoldingModel
import com.yandex.daggerlite.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.model.Variant
import com.yandex.daggerlite.core.model.Variant.DimensionModel
import com.yandex.daggerlite.core.model.Variant.FlavorModel
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.buildError

internal sealed interface VariantMatch {
    val conditionScope: ConditionScope

    class Matched(override val conditionScope: ConditionScope) : VariantMatch
    class Error(val message: ValidationMessage?) : VariantMatch {
        override val conditionScope: ConditionScope
            get() = ConditionScope.NeverScoped
    }
}

private val MatchedUnscoped = VariantMatch.Matched(ConditionScope.Unscoped)
private val MatchedNeverScoped = VariantMatch.Matched(ConditionScope.NeverScoped)

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
    return bestMatch?.featureTypes?.fold(ConditionScope.Unscoped as ConditionScope) { acc, featureType ->
        acc and featureType.conditionScope
    }?.let(VariantMatch::Matched) ?: MatchedNeverScoped
}