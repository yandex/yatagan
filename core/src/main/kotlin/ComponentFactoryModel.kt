package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Component.Factory].
 */
abstract class ComponentFactoryModel : NodeModel() {

    abstract val createdComponent: ComponentModel

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
    final override fun implicitBinding(): Nothing? = null
}