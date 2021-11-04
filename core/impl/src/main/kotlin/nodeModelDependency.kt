package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal fun nodeModelDependency(
    type: TypeLangModel,
    forQualifier: AnnotatedLangModel,
): NodeModel.Dependency {
    val kind = when (type.declaration.qualifiedName) {
        Names.Lazy -> Kind.Lazy
        Names.Provider -> Kind.Provider
        Names.Optional -> when (type.typeArguments.first().declaration.qualifiedName) {
            Names.Lazy -> Kind.OptionalLazy
            Names.Provider -> Kind.OptionalProvider
            else -> Kind.Optional
        }
        else -> Kind.Direct
    }
    return NodeModel.Dependency(
        node = NodeModelImpl(
            type = when (kind) {
                Kind.Direct -> type
                Kind.OptionalProvider, Kind.OptionalLazy -> type.typeArguments.first().typeArguments.first()
                Kind.Lazy, Kind.Provider, Kind.Optional -> type.typeArguments.first()
            },
            forQualifier = forQualifier,
        ),
        kind = kind,
    )
}

private object Names {
    val Lazy: String = com.yandex.daggerlite.Lazy::class.qualifiedName!!
    val Provider: String = javax.inject.Provider::class.qualifiedName!!
    val Optional: String = com.yandex.daggerlite.Optional::class.qualifiedName!!
}
