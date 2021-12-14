package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload.Dependency
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload.Instance
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload.Module
import com.yandex.daggerlite.core.lang.FunctionLangModel

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
    val factoryInputs: Collection<FactoryInputModel>

    /**
     * The component creating function to implement.
     */
    val factoryMethod: FunctionLangModel

    /**
     * TODO: doc.
     */
    val builderInputs: Collection<BuilderInputModel>

    /**
     * TODO: doc
     */
    fun asNode(): NodeModel

    /**
     * Encodes actual input data model, that [InputModel] introduces to the graph.
     * May be [Module], [Instance], [Dependency].
     *
     * @see InputModel
     */
    sealed interface InputPayload : ClassBackedModel {
        /**
         * Represent an externally given instance via [com.yandex.daggerlite.BindsInstance].
         */
        class Instance(val node: NodeModel) : InputPayload, ClassBackedModel by node

        /**
         * A [Module][ModuleModel] for which [ModuleModel.requiresInstance] holds `true` and it should be externally
         * supplied as a factory input.
         */
        class Module(val module: ModuleModel) : InputPayload, ClassBackedModel by module

        /**
         * An external component dependency.
         *
         * @see ComponentDependencyModel
         */
        class Dependency(val dependency: ComponentDependencyModel) : InputPayload, ClassBackedModel by dependency
    }

    /**
     * A [named][name] input with a [payload].
     *
     * @see InputPayload
     */
    interface InputModel {
        /**
         * @see InputPayload
         */
        val payload: InputPayload

        /**
         * An input name. May not be unique across [all inputs][allInputs] for the factory.
         */
        val name: String
    }

    /**
     * Represents an input parameter of the factory method.
     */
    interface FactoryInputModel : InputModel

    /**
     * Represents a setter method for the input in builder-like fashion.
     */
    interface BuilderInputModel : InputModel {
        /**
         * Actual abstract setter.
         * Has *single parameter*; return type may be of the builder type itself or
         * [void][com.yandex.daggerlite.core.lang.TypeLangModel.isVoid].
         */
        val builderSetter: FunctionLangModel
    }
}