package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Constructor

/**
 * A model for type that has [javax.inject.Inject]-annotated constructor.
 */
interface InjectConstructorModel : ConditionalHoldingModel, HasNodeModel {
    val constructor: Constructor
    val scopes: Set<Annotation>
    val inputs: List<NodeDependency>
}