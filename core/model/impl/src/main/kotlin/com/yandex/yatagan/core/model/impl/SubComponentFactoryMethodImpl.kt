/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.SubComponentFactoryMethodModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.scope.invoke
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

internal class SubComponentFactoryMethodImpl(
    override val factoryMethod: Method,
) : ComponentFactoryModelBase(), SubComponentFactoryMethodModel {
    override val createdComponent = ComponentModelImpl(factoryMethod.returnType.declaration)

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-factory-method",
        representation = factoryMethod,
    )

    override fun <R> accept(visitor: ComponentFactoryModel.Visitor<R>): R {
        return visitor.visitSubComponentFactoryMethod(this)
    }

    companion object {
        fun canRepresent(method: Method): Boolean {
            if (!method.isAbstract) return false

            // If no parameters, then it's rather an entry-point than factory.
            if (method.parameters.none()) return false

            val returnType = method.returnType.declaration
            if (!ComponentModelImpl.canRepresent(returnType))
                return false

            return !ComponentModelImpl(returnType).isRoot
        }
    }
}