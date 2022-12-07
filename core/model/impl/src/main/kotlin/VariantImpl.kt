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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.core.model.Variant.DimensionModel
import com.yandex.yatagan.core.model.Variant.FlavorModel
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class VariantImpl private constructor(
    private val parts: Map<DimensionModel, List<FlavorModel>>,
) : Variant {
    constructor(flavors: List<Type>)
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