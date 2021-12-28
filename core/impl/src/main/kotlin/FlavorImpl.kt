package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.buildError

internal class FlavorImpl private constructor(
    override val type: TypeLangModel,
) : Variant.FlavorModel {

    override val dimension: Variant.DimensionModel =
        type.declaration.componentFlavorIfPresent?.dimension?.let { DimensionImpl(it) } ?: MissingDimension(this)

    override fun validate(validator: Validator) {
        validator.child(dimension)

        if (type.declaration.componentFlavorIfPresent == null) {
            validator.report(buildError {
                contents = Errors.`declaration is not annotated with @ComponentFlavor`()
            })
        }
    }

    override fun toString() = "Flavor[$type]"

    companion object Factory : ObjectCache<TypeLangModel, FlavorImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type, ::FlavorImpl)
    }
}