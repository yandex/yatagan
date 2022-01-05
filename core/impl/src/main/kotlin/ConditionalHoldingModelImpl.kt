package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.FeatureModel
import com.yandex.daggerlite.core.Variant.FlavorModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError

internal open class ConditionalHoldingModelImpl(
    sources: Sequence<ConditionalAnnotationLangModel>,
) : ConditionalHoldingModel {
    final override val conditionals: Sequence<ConditionalWithFlavorConstraintsModel> =
        sources.map { annotation ->
            ConditionalWithFlavorConstraintsModelImpl(annotation)
        }

    private class ConditionalWithFlavorConstraintsModelImpl(
        annotation: ConditionalAnnotationLangModel,
    ) : ConditionalWithFlavorConstraintsModel {
        override val onlyIn: Sequence<FlavorModel> =
            annotation.onlyIn.map { FlavorImpl(it) }
        override val featureTypes: Sequence<FeatureModel> =
            annotation.featureTypes.map { FeatureModelImpl(it.declaration) }

        override fun validate(validator: Validator) {
            onlyIn.forEach(validator::child)
            featureTypes.forEach(validator::child)
        }
    }

    override fun validate(validator: Validator) {
        conditionals.forEach { validator.child(it, Inline) }
    }

    private class FeatureModelImpl private constructor(
        private val impl: TypeDeclarationLangModel,
    ) : FeatureModel {
        override val conditions: Sequence<ConditionLangModel> = impl.conditions

        override fun validate(validator: Validator) {
            if (conditions.none()) {
                validator.reportError(Errors.`no conditions on feature`())
            }
        }

        override fun toString() = "[feature] $impl"

        companion object Factory : ObjectCache<TypeDeclarationLangModel, FeatureModelImpl>() {
            operator fun invoke(impl: TypeDeclarationLangModel) = createCached(impl, ::FeatureModelImpl)
        }
    }
}