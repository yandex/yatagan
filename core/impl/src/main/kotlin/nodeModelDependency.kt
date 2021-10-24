package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Lazy
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.inject.Provider

internal fun nodeModelDependency(
    type: TypeLangModel,
    forQualifier: AnnotatedLangModel,
): NodeModel.Dependency {
    val kind = when (type.declaration.qualifiedName) {
        Lazy::class.qualifiedName -> NodeModel.Dependency.Kind.Lazy
        Provider::class.qualifiedName -> NodeModel.Dependency.Kind.Provider
        else -> NodeModel.Dependency.Kind.Direct
    }
    return NodeModel.Dependency(
        node = NodeModelImpl(
            type = when (kind) {
                NodeModel.Dependency.Kind.Direct -> type
                else -> type.typeArguments.first()
            },
            forQualifier = forQualifier,
        ),
        kind = kind,
    )
}