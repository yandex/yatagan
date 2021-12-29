package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportWarning
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentDependencyModelImpl private constructor(
    override val type: TypeLangModel,
) : ComponentDependencyModel {

    private val exposedEntryPoints: Map<NodeDependency, FunctionLangModel> by lazy(NONE) {
        type.declaration.allPublicFunctions.filter {
            it.parameters.none() && !it.returnType.isVoid
        }.associateBy { function ->
            NodeDependency(type = function.returnType, forQualifier = function)
        }
    }

    override val exposedDependencies: Map<NodeModel, FunctionLangModel> by lazy(NONE) {
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
        validator.child(node = asNode(), kind = Inline)

        if (!type.declaration.isAbstract) {
            validator.reportWarning(Strings.Warnings.`non-abstract dependency declaration`())
        }

        exposedEntryPoints.forEach { (dependency, function) ->
            if (dependency.kind != DependencyKind.Direct) {
                validator.reportWarning(Strings.Warnings.`exposed dependency of a framework type`(
                    functionName = function.name,
                    returnType = function.returnType,
                ))
            }
        }
    }

    override fun toString() = "Dependency[$type]"

    companion object Factory : ObjectCache<TypeLangModel, ComponentDependencyModelImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type) {
            ComponentDependencyModelImpl(type)
        }
    }
}