package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel
import com.yandex.daggerlite.core.ConditionalHoldingModel.FeatureModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

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
        override val onlyIn: Sequence<Variant.FlavorModel> =
            annotation.onlyIn.map { FlavorImpl(it) }
        override val featureTypes: Sequence<FeatureModel> =
            annotation.featureTypes.map { FeatureModelImpl(it.declaration) }
    }

    private class FeatureModelImpl private constructor(
        impl: TypeDeclarationLangModel,
    ) : FeatureModel {
        override val conditions: Sequence<ConditionLangModel> = impl.conditions

        init {
            require(conditions.any()) {
                "No conditions present on feature declaration $impl"
            }
        }

        companion object Factory : ObjectCache<TypeDeclarationLangModel, FeatureModelImpl>() {
            operator fun invoke(impl: TypeDeclarationLangModel) = createCached(impl, ::FeatureModelImpl)
        }
    }
}