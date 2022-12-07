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

import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel.FeatureModel
import com.yandex.yatagan.core.model.Variant.FlavorModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation

internal open class ConditionalHoldingModelImpl(
    sources: List<BuiltinAnnotation.Conditional>,
) : ConditionalHoldingModel {
    final override val conditionals: List<ConditionalWithFlavorConstraintsModel> = sources.map { annotation ->
        ConditionalWithFlavorConstraintsModelImpl(annotation)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "its conditions declaration",
        representation = {
            append("{ ")
            when (childContext) {
                is FlavorModel -> {
                    append("component-flavor-constraints (onlyIn): [.., ")
                    appendChildContextReference(reference = childContext.type)
                    append(", ..]")
                }
                is FeatureModel -> {
                    append("runtime-conditions: [.., ")
                    appendChildContextReference(reference = childContext.type)
                    append(", ..]")
                }
                else -> append("...")
            }
            append(" }")
        },
    )

    private class ConditionalWithFlavorConstraintsModelImpl(
        annotation: BuiltinAnnotation.Conditional,
    ) : ConditionalWithFlavorConstraintsModel {
        override val onlyIn: List<FlavorModel> =
            annotation.onlyIn.map { FlavorImpl(it) }
        override val featureTypes: List<FeatureModel> =
            annotation.featureTypes.map { FeatureModelImpl(it.declaration) }

        override fun validate(validator: Validator) {
            onlyIn.forEach(validator::child)
            featureTypes.forEach(validator::child)
        }

        override fun toString(childContext: MayBeInvalid?) = throw AssertionError("not reached")
    }

    override fun validate(validator: Validator) {
        conditionals.forEach(validator::inline)
    }

}