package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel

internal fun matchConditionScopeFromConditionals(
    conditionals: Sequence<ConditionalAnnotationLangModel>,
    forVariant: Variant,
): ConditionScope? {
    var maxMatched = -1
    var bestMatch: ConditionalAnnotationLangModel? = null
    outer@ for (conditional in conditionals) {
        val variant = VariantImpl(conditional.onlyIn)
        var currentMatch = 0
        for ((dimension, element) in variant.parts) {
            if (forVariant.parts[dimension] != element) {
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