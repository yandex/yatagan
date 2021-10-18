package com.yandex.dagger3.core

/**
 * Represents [dagger.Component.Factory].
 */
abstract class ComponentFactoryModel : NodeModel() {

    abstract val target: ComponentModel

    /**
     * Factory function inputs.
     */
    abstract val inputs: Collection<Input>

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

    final override val qualifier: Nothing? get() = null
    final override val defaultBinding: Nothing? get() = null
}