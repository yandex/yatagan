package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.ConstructorLangModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents [com.yandex.daggerlite.AssistedFactory].
 */
interface AssistedInjectFactoryModel : MayBeInvalid, HasNodeModel {
    /**
     * Factory method that takes assisted parameters and creates the instance via [assistedInjectConstructor],
     * passing "assisted" parameters as is, providing non-assisted (injected) dependencies from a graph.
     */
    val factoryMethod: FunctionLangModel?

    /**
     * an [@AssistedInject][com.yandex.daggerlite.AssistedInject]-annotated constructor from the
     * factory's target type.
     */
    val assistedInjectConstructor: ConstructorLangModel?

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