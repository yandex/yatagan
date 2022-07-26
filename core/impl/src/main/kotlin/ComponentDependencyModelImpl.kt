package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportWarning

internal class ComponentDependencyModelImpl private constructor(
    override val type: TypeLangModel,
) : ComponentDependencyModel {

    private val exposedEntryPoints: Map<NodeDependency, FunctionLangModel> by lazy {
        type.declaration.functions.filter {
            it.parameters.none() && !it.returnType.isVoid
        }.associateBy { function ->
            NodeDependency(type = function.returnType, forQualifier = function)
        }
    }

    override val exposedDependencies: Map<NodeModel, FunctionLangModel> by lazy {
        buildMap {
            exposedEntryPoints.forEach { (dependency, function) ->
                if (dependency.kind == DependencyKind.Direct) {
                    put(dependency.node, function)
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

        exposedEntryPoints.forEach { (dependency, function) ->
            if (dependency.kind != DependencyKind.Direct) {
                validator.reportWarning(Strings.Warnings.ignoredDependencyOfFrameworkType(
                    function = function,
                ))
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency",
        representation = type,
    )

    companion object Factory : ObjectCache<TypeLangModel, ComponentDependencyModelImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type) {
            ComponentDependencyModelImpl(type)
        }
    }
}