/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.model

import com.yandex.yatagan.base.api.NullIfInvalid
import com.yandex.yatagan.base.api.StableForImplementation
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Dependency
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Instance
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload.Module
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Models a factory method which creates a component instance.
 * Accepts [ModuleModel], [ComponentDependencyModel] and bound instances as parameters.
 *
 * This interface is fit to model a child component factory method. Its extension - [ComponentFactoryWithBuilderModel] -
 * models `@Component.Builder` declaration.
 */
public interface ComponentFactoryModel : MayBeInvalid {

    /**
     * A component which the factory produces.
     */
    public val createdComponent: ComponentModel

    /**
     * Factory method's inputs.
     */
    public val factoryInputs: Collection<FactoryInputModel>

    /**
     * The component creating method to implement.
     */
    @NullIfInvalid
    public val factoryMethod: Method?

    /**
     * Encodes actual input data model, that [InputModel] introduces to the graph.
     * May be [Module], [Instance], [Dependency].
     *
     * @see InputModel
     */
    public sealed interface InputPayload : MayBeInvalid {
        public val model: ClassBackedModel

        /**
         * Represent an externally given instance via [com.yandex.yatagan.BindsInstance].
         */
        public interface Instance : InputPayload {
            override val model: NodeModel
        }

        /**
         * A [Module][ModuleModel] for which [ModuleModel.requiresInstance] holds `true` and it should be externally
         * supplied as a factory input.
         */
        public interface Module : InputPayload {
            override val model: ModuleModel
        }

        /**
         * An external component dependency.
         *
         * @see ComponentDependencyModel
         */
        public interface Dependency : InputPayload {
            override val model: ComponentDependencyModel
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
     * Represents an input parameter of the factory.
     */
    public interface FactoryInputModel : InputModel

    public fun <R> accept(visitor: Visitor<R>): R

    @StableForImplementation
    public interface Visitor<R> {
        public fun visitOther(model: ComponentFactoryModel): R
        public fun visitSubComponentFactoryMethod(model: SubComponentFactoryMethodModel): R = visitOther(model)
        public fun visitWithBuilder(model: ComponentFactoryWithBuilderModel): R = visitOther(model)
    }
}