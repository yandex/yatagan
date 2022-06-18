package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.IntoMap
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.DependencyKind.Direct
import com.yandex.daggerlite.core.DependencyKind.Lazy
import com.yandex.daggerlite.core.DependencyKind.Optional
import com.yandex.daggerlite.core.DependencyKind.OptionalLazy
import com.yandex.daggerlite.core.DependencyKind.OptionalProvider
import com.yandex.daggerlite.core.DependencyKind.Provider
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import javax.inject.Qualifier
import javax.inject.Scope

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
    val node = NodeModelImpl(
        type = when (kind) {
            Direct -> type
            OptionalProvider, OptionalLazy -> type.typeArguments.first().typeArguments.first()
            Lazy, Provider, Optional -> type.typeArguments.first()
        },
        forQualifier = forQualifier,
    )
    return when (kind) {
        Direct -> node
        else -> NodeDependencyImpl(
            node = node,
            kind = kind,
        )
    }
}

internal fun isFrameworkType(type: TypeLangModel) = when (type.declaration.qualifiedName) {
    Names.Lazy, Names.Optional, Names.Provider -> true
    else -> false
}

internal object Names {
    val Lazy: String = com.yandex.daggerlite.Lazy::class.qualifiedName!!
    val Provider: String = javax.inject.Provider::class.qualifiedName!!
    val Optional: String = com.yandex.daggerlite.Optional::class.qualifiedName!!
}

private class NodeDependencyImpl(
    override val node: NodeModel,
    override val kind: DependencyKind,
) : NodeDependency {
    override fun toString() = "$node [$kind]"

    override fun replaceNode(node: NodeModel): NodeDependency {
        return NodeDependencyImpl(node = node, kind = kind)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is NodeDependencyImpl && node == other.node && kind == other.kind)
    }

    override fun hashCode(): Int = 31 * node.hashCode() + kind.hashCode()
}

internal fun AnnotationLangModel.isScope() = annotationClass.isAnnotatedWith<Scope>()

internal fun AnnotationLangModel.isQualifier() = annotationClass.isAnnotatedWith<Qualifier>()

internal fun AnnotationLangModel.isMapKey() = annotationClass.isAnnotatedWith<IntoMap.Key>()