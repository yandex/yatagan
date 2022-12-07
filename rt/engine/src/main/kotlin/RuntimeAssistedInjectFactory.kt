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

package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.lang.rt.rt
import com.yandex.yatagan.rt.support.DynamicValidationDelegate

internal class RuntimeAssistedInjectFactory(
    private val model: AssistedInjectFactoryModel,
    private val owner: RuntimeComponent,
    validationPromise: DynamicValidationDelegate.Promise?,
) : InvocationHandlerBase(validationPromise) {
    init {
        model.factoryMethod?.let { factoryMethod ->
            implementMethod(factoryMethod.rt, FactoryMethodHandler())
        }
    }

    private inner class FactoryMethodHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any? {
            val params = model.assistedConstructorParameters
            return checkNotNull(model.assistedInjectConstructor).rt.newInstance(
                *Array(params.size) { index ->
                    when (val parameter = params[index]) {
                        is AssistedInjectFactoryModel.Parameter.Assisted -> {
                            val argIndex = model.assistedFactoryParameters.indexOf(parameter)
                            check(argIndex >= 0)
                            args!![argIndex]
                        }
                        is AssistedInjectFactoryModel.Parameter.Injected -> {
                            owner.resolveAndAccess(parameter.dependency)
                        }
                    }
                }
            )
        }
    }
}