package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.core.model.DependencyKind
import com.yandex.daggerlite.core.model.DependencyKind.Direct
import com.yandex.daggerlite.core.model.DependencyKind.Lazy
import com.yandex.daggerlite.core.model.DependencyKind.Optional
import com.yandex.daggerlite.core.model.DependencyKind.OptionalLazy
import com.yandex.daggerlite.core.model.DependencyKind.OptionalProvider
import com.yandex.daggerlite.core.model.DependencyKind.Provider
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.Annotated
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.buildRichString

internal fun NodeDependency(
    type: Type,
    forQualifier: Annotated,
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

internal fun isFrameworkType(type: Type) = when (type.declaration.qualifiedName) {
    Names.Lazy, Names.Optional, Names.Provider -> true
    else -> false
}

internal object Names {
    const val Lazy: String = "com.yandex.daggerlite.Lazy"
    const val Provider: String = "javax.inject.Provider"
    const val Optional: String = "com.yandex.daggerlite.Optional"

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

internal fun Annotation.isScope() = annotationClass.getAnnotation(BuiltinAnnotation.Scope) != null

internal fun Annotation.isQualifier() = annotationClass.getAnnotation(BuiltinAnnotation.Qualifier) != null

internal fun Annotation.isMapKey() = annotationClass.getAnnotation(BuiltinAnnotation.IntoMap.Key) != null