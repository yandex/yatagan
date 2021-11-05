package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Component.Factory].
 */
interface ComponentFactoryModel : ClassBackedModel {

    /**
     * TODO: doc.
     */
    val createdComponent: ComponentModel

    /**
     * Factory function inputs.
     */
    val inputs: Collection<Input>

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
         * parameter name from the factory function.
         */
        val paramName: String
    }
}