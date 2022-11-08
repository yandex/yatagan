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