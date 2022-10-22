package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.core.model.Variant
import com.yandex.daggerlite.core.model.Variant.DimensionModel
import com.yandex.daggerlite.core.model.Variant.FlavorModel
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class VariantImpl private constructor(
    private val parts: Map<DimensionModel, List<FlavorModel>>,
) : Variant {
    constructor(flavors: Sequence<TypeLangModel>)
            : this(flavors.map { FlavorImpl(it) }.groupBy(FlavorImpl::dimension))

    override fun plus(variant: Variant?): Variant {
        // TODO: Implement variant extension explicitly, rather than by simple merging, when all hierarchy info is lost.
        variant ?: return this
        return VariantImpl(buildMap<DimensionModel, MutableList<FlavorModel>> {
            parts.forEach { (dimension, flavor) ->
                getOrPut(dimension, ::arrayListOf) += flavor
            }
            variant.asMap().forEach { (dimension, flavor) ->
                getOrPut(dimension, ::arrayListOf) += flavor
            }
        })
    }

    override fun get(dimension: DimensionModel): FlavorModel? {
        return parts[dimension]?.first()
    }

    override fun asMap(): Map<DimensionModel, FlavorModel> {
        return parts.mapValues { (_, flavors) -> flavors.first() }
    }

    override fun validate(validator: Validator) {
        parts.forEach { (dimension: DimensionModel, flavors: List<FlavorModel>) ->
            flavors.forEach(validator::child)
            if (flavors.size > 1) {
                validator.reportError(
                    Strings.Errors.conflictingOrDuplicateFlavors(dimension = dimension)) {
                    for (conflict in flavors) {
                        addNote(Strings.Notes.conflictingFlavorInVariant(flavor = conflict))
                    }
                }
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-variant",
        representation = {
            when {
                childContext != null -> {
                    val (dimension, _) = parts.entries.find { (_, flavor) ->
                        childContext == flavor
                    }!!
                    append("{.., ")
                    appendChildContextReference(reference = buildRichString {
                        append("flavor for ")
                        append(dimension)
                    })
                    append(", ..}")
                }
                parts.isEmpty() -> append("{empty}")
                else -> append("{...}")
            }
        }
    )
}