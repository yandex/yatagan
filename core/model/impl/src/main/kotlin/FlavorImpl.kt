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