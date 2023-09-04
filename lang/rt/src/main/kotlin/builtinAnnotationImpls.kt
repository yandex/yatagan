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

@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.AnyCondition
import com.yandex.yatagan.Assisted
import com.yandex.yatagan.Component
import com.yandex.yatagan.ComponentFlavor
import com.yandex.yatagan.Condition
import com.yandex.yatagan.ConditionExpression
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.IntoSet
import com.yandex.yatagan.Module
import com.yandex.yatagan.Provides
import com.yandex.yatagan.VariantApi
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type

internal open class RtAnnotationImplBase<A : Annotation>(
    protected val impl: A,
) {
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?) = this === other || (other is RtAnnotationImplBase<*> && impl == other.impl)
    final override fun toString() = impl.toString()
}

internal class RtComponentAnnotationImpl(
    impl: Component,
) : RtAnnotationImplBase<Component>(impl), BuiltinAnnotation.Component {
    override val isRoot: Boolean get() = impl.isRoot
    override val modules get() = impl.modules.map { RtTypeImpl(it.java) }
    override val dependencies get() = impl.dependencies.map { RtTypeImpl(it.java) }
    override val variant get() = impl.variant.map { RtTypeImpl(it.java) }
    override val multiThreadAccess: Boolean get() = impl.multiThreadAccess
}

internal class RtModuleAnnotationImpl(
    impl: Module,
) : RtAnnotationImplBase<Module>(impl), BuiltinAnnotation.Module {
    override val includes
        get() = impl.includes.map { RtTypeImpl(it.java) }
    override val subcomponents
        get() = impl.subcomponents.map { RtTypeImpl(it.java) }
}

internal class RtConditionalAnnotationImpl(
    impl: Conditional,
) : RtAnnotationImplBase<Conditional>(impl), BuiltinAnnotation.Conditional {
    override val featureTypes get() = impl.value.map { RtTypeImpl(it.java) }
    override val onlyIn get() = impl.onlyIn.map { RtTypeImpl(it.java) }
}

internal class RtProvidesAnnotationImpl(
    impl: Provides,
) : RtAnnotationImplBase<Provides>(impl), BuiltinAnnotation.Provides {
    override val conditionals get() = impl.value.map {
        RtConditionalAnnotationImpl(it)
    }
}

internal class RtIntoListAnnotationImpl(
    impl: IntoList,
) : RtAnnotationImplBase<IntoList>(impl), BuiltinAnnotation.IntoCollectionFamily.IntoList {
    override val flatten: Boolean
        get() = impl.flatten
}

internal class RtIntoSetAnnotationImpl(
    impl: IntoSet,
) : RtAnnotationImplBase<IntoSet>(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
    override val flatten: Boolean
        get() = impl.flatten
}

internal class RtConditionAnnotationImpl(
    impl: Condition,
) : RtAnnotationImplBase<Condition>(impl), BuiltinAnnotation.ConditionFamily.One {
    override val target: Type
        get() = RtTypeImpl(impl.value.java)
    override val condition: String
        get() = impl.condition
}

internal class RtAnyConditionAnnotationImpl(
    impl: AnyCondition,
) : RtAnnotationImplBase<AnyCondition>(impl), BuiltinAnnotation.ConditionFamily.Any {
    override val conditions get() = impl.value.map {
        RtConditionAnnotationImpl(it)
    }
}

internal class RtConditionExpressionAnnotationImpl(
    impl: ConditionExpression,
) : RtAnnotationImplBase<ConditionExpression>(impl), BuiltinAnnotation.ConditionExpression {
    override val value: String
        get() = impl.value
    override val imports: List<Type>
        get() = impl.imports.map { RtTypeImpl(it.java) }
    override val importAs: List<BuiltinAnnotation.ConditionExpression.ImportAs> by lazy {
        impl.importAs.map { ImportAsImpl(it) }
    }

    private data class ImportAsImpl(
        private val impl: ConditionExpression.ImportAs,
    ) : BuiltinAnnotation.ConditionExpression.ImportAs {
        override val value: Type = RtTypeImpl(impl.value.java)
        override val alias: String = impl.alias
    }
}

internal class RtComponentFlavorAnnotationImpl(
    impl: ComponentFlavor,
) : RtAnnotationImplBase<ComponentFlavor>(impl), BuiltinAnnotation.ComponentFlavor {
    override val dimension: Type
        get() = RtTypeImpl(impl.dimension.java)
}

internal class RtAssistedAnnotationImpl(
    impl: Assisted,
) : RtAnnotationImplBase<Assisted>(impl), BuiltinAnnotation.Assisted {
    override val value: String
        get() = impl.value
}
