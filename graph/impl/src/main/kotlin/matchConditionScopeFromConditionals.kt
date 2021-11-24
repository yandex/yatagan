package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.Variant.DimensionModel
import com.yandex.daggerlite.core.Variant.FlavorModel

internal fun ConditionalHoldingModel.conditionScopeFor(
    forVariant: Variant,
): ConditionScope? {
    if (conditionals.none()) {
        return ConditionScope.Unscoped
    }

    var maxMatched = -1
    var bestMatch: ConditionalWithFlavorConstraintsModel? = null
    outer@ for (conditional in conditionals) {
        val constraints: Map<DimensionModel, List<FlavorModel>> = conditional.onlyIn
            .groupBy(FlavorModel::dimension)
        var currentMatch = 0
        for ((dimension: DimensionModel, allowedFlavors: List<FlavorModel>) in constraints) {
            val flavor = forVariant[dimension]
            check(flavor != null) {
                "Dimension $dimension doesn't exist in $forVariant"
            }
            if (flavor !in allowedFlavors) {
                // Not matched
                continue@outer
            }
            ++currentMatch
        }
        check(maxMatched != currentMatch) {
            "Variant matching ambiguity: $bestMatch and $conditional are in conflict"
        }
        if (maxMatched < currentMatch) {
            maxMatched = currentMatch
            bestMatch = conditional
        }
    }
    bestMatch ?: return null
    return bestMatch.featureTypes.fold(ConditionScope.Unscoped) { acc, featureType ->
        acc and ConditionScope(featureType.conditions)
    }
}