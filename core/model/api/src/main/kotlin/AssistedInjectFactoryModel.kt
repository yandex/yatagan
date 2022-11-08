package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents [com.yandex.yatagan.AssistedFactory].
 */
interface AssistedInjectFactoryModel : MayBeInvalid, HasNodeModel {
    /**
     * Factory method that takes assisted parameters and creates the instance via [assistedInjectConstructor],
     * passing "assisted" parameters as is, providing non-assisted (injected) dependencies from a graph.
     */
    val factoryMethod: Method?

    /**
     * an [@AssistedInject][com.yandex.yatagan.AssistedInject]-annotated constructor from the
     * factory's target type.
     */
    val assistedInjectConstructor: Constructor?

    /**
     * Parsed parameter models from [assistedInjectConstructor].
     */
    val assistedConstructorParameters: List<Parameter>

    /**
     * Parsed parameter models from [factoryMethod].
     */
    val assistedFactoryParameters: List<Parameter.Assisted>

    sealed interface Parameter {
        /**
         * Models parameters, arguments for which are to be passed in externally.
         */
        data class Assisted(
            val identifier: String,
            val type: Type,
        ) : Parameter {
            override fun toString() = "@Assisted(\"$identifier\") $type"
        }

        /**
         * Model parameters, values for which are to be provided from the graph.
         */
        data class Injected(
            val dependency: NodeDependency,
        ) : Parameter
    }
}