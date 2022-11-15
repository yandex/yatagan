package com.yandex.yatagan.core.model

import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Dependency
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Instance
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Module
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents [com.yandex.yatagan.Component.Builder].
 */
public interface ComponentFactoryModel : MayBeInvalid, HasNodeModel {

    /**
     * TODO: doc.
     */
    public val createdComponent: ComponentModel

    /**
     * Factory method's inputs.
     */
    public val factoryInputs: Collection<FactoryInputModel>

    /**
     * The component creating method to implement.
     * `null` if no appropriate method is found.
     */
    public val factoryMethod: Method?

    /**
     * TODO: doc.
     */
    public val builderInputs: Collection<BuilderInputModel>

    /**
     * Encodes actual input data model, that [InputModel] introduces to the graph.
     * May be [Module], [Instance], [Dependency].
     *
     * @see InputModel
     */
    public sealed interface InputPayload : ClassBackedModel, MayBeInvalid {
        /**
         * Represent an externally given instance via [com.yandex.yatagan.BindsInstance].
         */
        public interface Instance : InputPayload {
            public val node: NodeModel
        }

        /**
         * A [Module][ModuleModel] for which [ModuleModel.requiresInstance] holds `true` and it should be externally
         * supplied as a factory input.
         */
        public interface Module : InputPayload {
            public val module: ModuleModel
        }

        /**
         * An external component dependency.
         *
         * @see ComponentDependencyModel
         */
        public interface Dependency : InputPayload {
            public val dependency: ComponentDependencyModel
        }
    }

    /**
     * A [named][name] input with a [payload].
     *
     * @see InputPayload
     */
    public interface InputModel : MayBeInvalid {
        /**
         * @see InputPayload
         */
        public val payload: InputPayload

        /**
         * An input name. May not be unique across [all inputs][allInputs] for the factory.
         */
        public val name: String
    }

    /**
     * Represents an input parameter of the factory method.
     */
    public interface FactoryInputModel : InputModel

    /**
     * Represents a setter method for the input in builder-like fashion.
     */
    public interface BuilderInputModel : InputModel {
        /**
         * Actual abstract setter.
         * Has *single parameter*; return type may be of the builder type itself or
         * [void][com.yandex.yatagan.lang.Type.isVoid].
         */
        public val builderSetter: Method
    }
}