package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class DimensionImpl private constructor(
    override val type: Type,
) : Variant.DimensionModel {

    override fun validate(validator: Validator) {
        if (type.declaration.getAnnotation(BuiltinAnnotation.ComponentVariantDimension) == null) {
            validator.reportError(Strings.Errors.nonComponentVariantDimension())
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-variant-dimension",
        representation = type,
    )

    override val isInvalid: Boolean
        get() = false

    companion object Factory : ObjectCache<Type, DimensionImpl>() {
        operator fun invoke(type: Type) = DimensionImpl.createCached(type, ::DimensionImpl)
    }
}

internal class MissingDimension(private val flavor: Variant.FlavorModel) : Variant.DimensionModel {
    override val type: Type
        get() = LangModelFactory.errorType

    override fun validate(validator: Validator) {
        // Do not report anything here, as not-a-flavor error will be reported for flavor.
    }

    override val isInvalid: Boolean
        get() = true

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-variant-dimension",
        representation = {
            color = TextColor.Red
            append("<missing>")
        }
    )

    override fun hashCode() = flavor.hashCode()

    override fun equals(other: Any?): Boolean {
        return this === other || (other is MissingDimension && other.flavor == flavor)
    }
}