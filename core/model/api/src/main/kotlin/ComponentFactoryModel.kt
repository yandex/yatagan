package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.core.model.ComponentFactoryModel.InputPayload.Dependency
import com.yandex.daggerlite.core.model.ComponentFactoryModel.InputPayload.Instance
import com.yandex.daggerlite.core.model.ComponentFactoryModel.InputPayload.Module
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents [com.yandex.daggerlite.Component.Builder].
 */
interface ComponentFactoryModel : MayBeInvalid, HasNodeModel {

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
     * `null` if no appropriate method is found.
     */
    val factoryMethod: FunctionLangModel?

    /**
     * TODO: doc.
     */
    val builderInputs: Collection<BuilderInputModel>

    /**
     * Encodes actual input data model, that [InputModel] introduces to the graph.
     * May be [Module], [Instance], [Dependency].
     *
     * @see InputModel
     */
    sealed interface InputPayload : ClassBackedModel, MayBeInvalid {
        /**
         * Represent an externally given instance via [com.yandex.daggerlite.BindsInstance].
         */
        interface Instance : InputPayload {
            val node: NodeModel
        }

        /**
         * A [Module][ModuleModel] for which [ModuleModel.requiresInstance] holds `true` and it should be externally
         * supplied as a factory input.
         */
        interface Module : InputPayload {
            val module: ModuleModel
        }

        /**
         * An external component dependency.
         *
         * @see ComponentDependencyModel
         */
        interface Dependency : InputPayload {
            val dependency: ComponentDependencyModel
        }
    }

    /**
     * A [named][name] input with a [payload].
     *
     * @see InputPayload
     */
    interface InputModel : MayBeInvalid {
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
         * [void][com.yandex.daggerlite.lang.Type.isVoid].
         */
        val builderSetter: FunctionLangModel
    }
}