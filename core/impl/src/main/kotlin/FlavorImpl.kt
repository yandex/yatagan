package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class FlavorImpl private constructor(
    override val type: TypeLangModel,
) : Variant.FlavorModel {

    override val dimension: Variant.DimensionModel =
        type.declaration.componentFlavorIfPresent?.dimension?.let { DimensionImpl(it) } ?: MissingDimension(this)

    override fun validate(validator: Validator) {
        validator.child(dimension)

        if (type.declaration.componentFlavorIfPresent == null) {
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

    companion object Factory : ObjectCache<TypeLangModel, FlavorImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type, ::FlavorImpl)
    }
}