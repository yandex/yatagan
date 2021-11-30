package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

class ComponentDependencyModelImpl private constructor(
    override val type: TypeLangModel,
) : ComponentDependencyModel {

    override val exposedDependencies: Map<NodeModel, FunctionLangModel> by lazy(NONE) {
        buildMap {
            type.declaration.allPublicFunctions.filter {
                it.parameters.none()
            }.forEach { function ->
                val dependency = NodeDependency(type = function.returnType, forQualifier = function)
                if (dependency.kind == DependencyKind.Direct) {
                    // Only direct are supported.
                    put(dependency.node, function)
                } else {
                    // TODO: Issue warning/error
                }
            }
        }
    }

    override fun asNode(): NodeModel {
        return NodeModelImpl(type = type, qualifier = null)
    }

    companion object Factory : ObjectCache<TypeLangModel, ComponentDependencyModelImpl>() {
        operator fun invoke(type: TypeLangModel) = createCached(type) {
            ComponentDependencyModelImpl(type)
        }

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return true
        }
    }
}