package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.Constructor

/**
 * A model for type that has [javax.inject.Inject]-annotated constructor.
 */
interface InjectConstructorModel : ConditionalHoldingModel, HasNodeModel {
    val constructor: Constructor
    val scopes: Set<AnnotationLangModel>
    val inputs: List<NodeDependency>
}