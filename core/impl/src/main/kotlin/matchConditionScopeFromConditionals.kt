package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.Variant.DimensionModel
import com.yandex.daggerlite.core.Variant.FlavorModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel

internal fun matchConditionScopeFromConditionals(
    conditionals: Sequence<ConditionalAnnotationLangModel>,
    forVariant: Variant,
): ConditionScope? {
    var maxMatched = -1
    var bestMatch: ConditionalAnnotationLangModel? = null
    outer@ for (conditional in conditionals) {
        val constraints: Map<DimensionModel, List<FlavorModel>> = conditional.onlyIn
            .map { FlavorImpl(it) }
            .groupBy(FlavorImpl::dimension)
        var currentMatch = 0
        for ((dimension: DimensionModel, allowedFlavors: List<FlavorModel>) in constraints) {
            val flavor = forVariant.parts[dimension]
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
        acc and ConditionScope(featureType.declaration.conditions)
    }
}