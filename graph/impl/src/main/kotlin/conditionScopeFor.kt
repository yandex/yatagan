package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.Variant.DimensionModel
import com.yandex.daggerlite.core.Variant.FlavorModel
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.impl.buildError

internal sealed interface VariantMatch {
    val conditionScope: ConditionScope

    class Matched(override val conditionScope: ConditionScope) : VariantMatch
    class Error(val message: ValidationMessage) : VariantMatch {
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
            val flavor = forVariant[dimension] ?: return VariantMatch.Error(buildError {
                contents = "Dimension $dimension doesn't exist in $forVariant"
            })
            if (flavor !in allowedFlavors) {
                // Not matched
                continue@outer
            }
            ++currentMatch
        }
        if (maxMatched == currentMatch) {
            return VariantMatch.Error(buildError {
                contents = "Variant matching ambiguity: $bestMatch and $conditional are in conflict"
            })
        }
        if (maxMatched < currentMatch) {
            maxMatched = currentMatch
            bestMatch = conditional
        }
    }
    return bestMatch?.featureTypes?.fold(ConditionScope.Unscoped) { acc, featureType ->
        acc and ConditionScope(featureType.conditions)
    }?.let(VariantMatch::Matched) ?: MatchedNeverScoped
}