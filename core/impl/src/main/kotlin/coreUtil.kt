package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.DependencyKind.Direct
import com.yandex.daggerlite.core.DependencyKind.Lazy
import com.yandex.daggerlite.core.DependencyKind.Optional
import com.yandex.daggerlite.core.DependencyKind.OptionalLazy
import com.yandex.daggerlite.core.DependencyKind.OptionalProvider
import com.yandex.daggerlite.core.DependencyKind.Provider
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal fun NodeDependency(
    type: TypeLangModel,
    forQualifier: AnnotatedLangModel,
): NodeDependency {
    val kind = when (type.declaration.qualifiedName) {
        Names.Lazy -> Lazy
        Names.Provider -> Provider
        Names.Optional -> when (type.typeArguments.first().declaration.qualifiedName) {
            Names.Lazy -> OptionalLazy
            Names.Provider -> OptionalProvider
            else -> Optional
        }
        else -> Direct
    }
    return NodeDependency(
        node = NodeModelImpl(
            type = when (kind) {
                Direct -> type
                OptionalProvider, OptionalLazy -> type.typeArguments.first().typeArguments.first()
                Lazy, Provider, Optional -> type.typeArguments.first()
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
