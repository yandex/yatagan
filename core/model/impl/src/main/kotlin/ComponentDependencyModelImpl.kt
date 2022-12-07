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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportWarning

internal class ComponentDependencyModelImpl private constructor(
    override val type: Type,
) : ComponentDependencyModel {

    private val exposedEntryPoints: Map<NodeDependency, Method> by lazy {
        type.declaration.methods.filter {
            it.parameters.none() && !it.returnType.isVoid
        }.associateBy { method ->
            NodeDependency(type = method.returnType, forQualifier = method)
        }
    }

    override val exposedDependencies: Map<NodeModel, Method> by lazy {
        buildMap {
            exposedEntryPoints.forEach { (dependency, method) ->
                if (dependency.kind == DependencyKind.Direct) {
                    put(dependency.node, method)
                }
            }
        }
    }

    override fun asNode(): NodeModel {
        return NodeModelImpl(type = type)
    }

    override fun validate(validator: Validator) {
        validator.inline(node = asNode())

        if (!type.declaration.isAbstract) {
            validator.reportWarning(Strings.Warnings.nonAbstractDependency())
        }

        exposedEntryPoints.forEach { (dependency, method) ->
            if (dependency.kind != DependencyKind.Direct) {
                validator.reportWarning(Strings.Warnings.ignoredDependencyOfFrameworkType(
                    method = method,
                ))
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency",
        representation = type,
    )

    companion object Factory : ObjectCache<Type, ComponentDependencyModelImpl>() {
        operator fun invoke(type: Type) = createCached(type) {
            ComponentDependencyModelImpl(type)
        }
    }
}