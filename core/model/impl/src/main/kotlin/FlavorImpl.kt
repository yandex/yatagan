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

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class FlavorImpl private constructor(
    override val type: Type,
) : Variant.FlavorModel {

    override val dimension: Variant.DimensionModel =
        type.declaration.getAnnotation(BuiltinAnnotation.ComponentFlavor)
            ?.dimension?.let { DimensionImpl(it) } ?: MissingDimension(this)

    override fun validate(validator: Validator) {
        validator.child(dimension)

        if (type.declaration.getAnnotation(BuiltinAnnotation.ComponentFlavor) == null) {
            validator.reportError(Strings.Errors.nonFlavor())
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-flavor",
        representation = {
            append(type)
            if (childContext == dimension) {
                appendRichString {
                    color = TextColor.Gray
                    append(" belonging to ")
                }
                appendChildContextReference(reference = "<dimension>")
            }
        },
    )

    companion object Factory : ObjectCache<Type, FlavorImpl>() {
        operator fun invoke(type: Type) = createCached(type, ::FlavorImpl)
    }
}