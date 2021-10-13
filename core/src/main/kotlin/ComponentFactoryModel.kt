package com.yandex.dagger3.core

/**
 * Represents [dagger.Component.Factory].
 */
interface ComponentFactoryModel : ClassBackedModel {
    /**
     * Factory function inputs.
     */
    val inputs: Collection<Input>

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