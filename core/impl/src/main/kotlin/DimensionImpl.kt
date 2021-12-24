package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.buildError

internal class DimensionImpl private constructor(
    override val type: TypeLangModel,
) : Variant.DimensionModel {

    override fun toString() = "Dimension[$type]"

    override fun validate(validator: Validator) {
        if (!type.declaration.isAnnotatedWith<ComponentVariantDimension>()) {
            validator.report(buildError { contents = "$type is not a variant dimension" })
        }
    }

    companion object Factory : ObjectCache<TypeLangModel, DimensionImpl>() {
        operator fun invoke(type: TypeLangModel) = DimensionImpl.createCached(type, ::DimensionImpl)
    }
}

internal class MissingDimension(private val flavor: Variant.FlavorModel) : Variant.DimensionModel {
    override val type: TypeLangModel
        get() = LangModelFactory.errorType

    override fun validate(validator: Validator) {
        // Always invalid
        validator.report(buildError {
            contents = "Dimension for $flavor is unspecified"
        })
    }

    override fun toString() = "[missing dimension for $flavor]"

    override fun hashCode() = flavor.hashCode()

    override fun equals(other: Any?): Boolean {
        return this === other || (other is MissingDimension && other.flavor == flavor)
    }
}