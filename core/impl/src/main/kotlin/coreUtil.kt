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
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.buildRichString
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

    const val List: String = "java.util.List"
    const val Set: String = "java.util.Set"
    const val Map: String = "java.util.Map"
}

internal data class NodeDependencyImpl(
    override val node: NodeModel,
    override val kind: DependencyKind,
) : NodeDependency {
    override fun validate(validator: Validator) = Unit
    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Inherit
        appendRichString {
            color = TextColor.Cyan
            append("[$kind] ")
        }
        append(node)
    }
    override fun copyDependency(node: NodeModel, kind: DependencyKind) = copy(node = node, kind = kind)
}

internal fun AnnotationLangModel.isScope() = annotationClass.isAnnotatedWith<Scope>()

internal fun AnnotationLangModel.isQualifier() = annotationClass.isAnnotatedWith<Qualifier>()

internal fun AnnotationLangModel.isMapKey() = annotationClass.isAnnotatedWith<IntoMap.Key>()