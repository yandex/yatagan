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

import com.yandex.yatagan.core.model.ComponentEntryPoint
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation

internal class ComponentEntryPointImpl(
    override val getter: Method,
    override val dependency: NodeDependency,
) : ComponentEntryPoint {
    override fun validate(validator: Validator) {
        validator.child(dependency.node)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "entry-point",
        representation = {
            append("${getter.name}()")
            if (childContext == dependency.node) {
                append(": ")
                appendChildContextReference(reference = getter.returnType)
            }
        },
    )

    companion object {
        fun canRepresent(method: Method): Boolean {
            return method.isAbstract && method.parameters.none()
        }
    }
}