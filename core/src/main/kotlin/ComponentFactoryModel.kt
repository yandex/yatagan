package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Component.Builder].
 */
interface ComponentFactoryModel : ClassBackedModel {

    /**
     * TODO: doc.
     */
    val createdComponent: ComponentModel

    /**
     * Factory function inputs.
     */
    val factoryInputs: Collection<Input>

    /**
     * The name of the component creating function to implement.
     */
    val factoryFunctionName: String

    /**
     * TODO: doc.
     */
    val builderInputs: Collection<Input>

    /**
     * TODO: doc
     */
    fun asNode(): NodeModel

    /**
     * Represent an input parameter of the factory method.
     */
    sealed interface Input {
        /**
         * Parameter model. Overridden to concrete models in variants.
         */
        val target: ClassBackedModel

        /**
         * the parameter name from the factory function *OR*
         * the setter function name if builder setter.
         */
        val name: String
    }
}