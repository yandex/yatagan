package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Constructor

/**
 * A model for type that has [javax.inject.Inject]-annotated constructor.
 */
public interface InjectConstructorModel : ConditionalHoldingModel, HasNodeModel {
    public val constructor: Constructor
    public val scopes: Set<Annotation>
    public val inputs: List<NodeDependency>
}