/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.DependencyKind.Direct
import com.yandex.yatagan.core.model.DependencyKind.Lazy
import com.yandex.yatagan.core.model.DependencyKind.Optional
import com.yandex.yatagan.core.model.DependencyKind.OptionalLazy
import com.yandex.yatagan.core.model.DependencyKind.OptionalProvider
import com.yandex.yatagan.core.model.DependencyKind.Provider
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.buildRichString

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
    const val Lazy: String = "com.yandex.yatagan.Lazy"
    const val Provider: String = "javax.inject.Provider"
    const val Optional: String = "com.yandex.yatagan.Optional"

    const val Reusable: String = "com.yandex.yatagan.Reusable"

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

internal fun buildScopeModels(from: Annotated): Set<ScopeModel> {
    return from.annotations.mapNotNullTo(mutableSetOf()) {
        ifOrElseNull(it.isScope()) { ScopeModelImpl(it) }
    }
}