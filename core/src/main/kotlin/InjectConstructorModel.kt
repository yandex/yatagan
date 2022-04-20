package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel

/**
 * A model for type that has [javax.inject.Inject]-annotated constructor.
 */
interface InjectConstructorModel : ConditionalHoldingModel, HasNodeModel {
    val constructor: ConstructorLangModel
    val scope: AnnotationLangModel?
    val inputs: List<NodeDependency>
}