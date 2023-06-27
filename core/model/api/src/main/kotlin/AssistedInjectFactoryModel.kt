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
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type

/**
 * Represents [com.yandex.yatagan.AssistedFactory].
 */
public interface AssistedInjectFactoryModel : HasNodeModel, ConditionalHoldingModel {
    /**
     * Factory method that takes assisted parameters and creates the instance via [assistedInjectConstructor],
     * passing "assisted" parameters as is, providing non-assisted (injected) dependencies from a graph.
     */
    @NullIfInvalid
    public val factoryMethod: Method?

    /**
     * an [@AssistedInject][com.yandex.yatagan.AssistedInject]-annotated constructor from the
     * factory's target type.
     */
    @NullIfInvalid
    public val assistedInjectConstructor: Constructor?

    /**
     * Parsed parameter models from [assistedInjectConstructor].
     */
    public val assistedConstructorParameters: List<Parameter>

    /**
     * Parsed parameter models from [factoryMethod].
     */
    public val assistedFactoryParameters: List<Parameter.Assisted>

    public sealed interface Parameter {
        /**
         * Models parameters, arguments for which are to be passed in externally.
         */
        public data class Assisted(
            val identifier: String,
            val type: Type,
        ) : Parameter {
            override fun toString(): String = "@Assisted(\"$identifier\") $type"
        }

        /**
         * Model parameters, values for which are to be provided from the graph.
         */
        public data class Injected(
            val dependency: NodeDependency,
        ) : Parameter
    }
}