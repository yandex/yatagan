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

import com.yandex.yatagan.Assisted
import com.yandex.yatagan.Component
import com.yandex.yatagan.ComponentFlavor
import com.yandex.yatagan.ConditionExpression
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.IntoSet
import com.yandex.yatagan.Module
import com.yandex.yatagan.Provides
import com.yandex.yatagan.ValueOf
import com.yandex.yatagan.VariantApi
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope

internal open class RtAnnotationImplBase<A : Annotation>(
    protected val impl: A,
) {
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?) = this === other || (other is RtAnnotationImplBase<*> && impl == other.impl)
    final override fun toString() = impl.toString()
}

internal class RtComponentAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: Component,
) : RtAnnotationImplBase<Component>(impl), BuiltinAnnotation.Component, LexicalScope by lexicalScope {
    override val isRoot: Boolean get() = impl.isRoot
    override val modules get() = impl.modules.map { RtTypeImpl(it.java) }
    override val dependencies get() = impl.dependencies.map { RtTypeImpl(it.java) }
    override val variant get() = impl.variant.map { RtTypeImpl(it.java) }
    override val multiThreadAccess: Boolean get() = impl.multiThreadAccess

    companion object Factory : FactoryKey<Component, RtComponentAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtComponentAnnotationImpl
    }
}

internal class RtModuleAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: Module,
) : RtAnnotationImplBase<Module>(impl), BuiltinAnnotation.Module, LexicalScope by lexicalScope {
    override val includes
        get() = impl.includes.map { RtTypeImpl(it.java) }
    override val subcomponents
        get() = impl.subcomponents.map { RtTypeImpl(it.java) }

    companion object Factory : FactoryKey<Module, RtModuleAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtModuleAnnotationImpl
    }
}

internal class RtConditionalAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: Conditional,
) : RtAnnotationImplBase<Conditional>(impl), BuiltinAnnotation.Conditional, LexicalScope by lexicalScope {
    override val featureTypes get() = impl.value.map { RtTypeImpl(it.java) }
    override val onlyIn get() = impl.onlyIn.map { RtTypeImpl(it.java) }

    companion object Factory : FactoryKey<Conditional, RtConditionalAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtConditionalAnnotationImpl
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

internal class RtConditionExpressionAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: ConditionExpression,
) : RtAnnotationImplBase<ConditionExpression>(impl), BuiltinAnnotation.ConditionExpression, LexicalScope by lexicalScope {
    override val value: String
        get() = impl.value
    override val imports: List<Type>
        get() = impl.imports.map { RtTypeImpl(it.java) }
    override val importAs: List<BuiltinAnnotation.ConditionExpression.ImportAs> by lazy {
        impl.importAs.map { ImportAsImpl(this, it) }
    }

    companion object Factory : FactoryKey<ConditionExpression, RtConditionExpressionAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtConditionExpressionAnnotationImpl
    }

    private class ImportAsImpl(
        lexicalScope: LexicalScope,
        impl: ConditionExpression.ImportAs,
    ) : BuiltinAnnotation.ConditionExpression.ImportAs, LexicalScope by lexicalScope {
        override val value: Type = RtTypeImpl(impl.value.java)
        override val alias: String = impl.alias

        override fun equals(other: Any?) =
            this === other || other is ImportAsImpl &&
                    other.value == value &&
                    other.alias == alias

        override fun hashCode(): Int = 31 * value.hashCode() + alias.hashCode()
    }
}

internal class RtValueOfAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: ValueOf,
) : RtAnnotationImplBase<ValueOf>(impl), BuiltinAnnotation.ValueOf, LexicalScope by lexicalScope {
    override val value: BuiltinAnnotation.ConditionExpression
        get() = RtConditionExpressionAnnotationImpl(impl.value)

    companion object Factory : FactoryKey<ValueOf, RtValueOfAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtValueOfAnnotationImpl
    }
}

internal class RtComponentFlavorAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    impl: ComponentFlavor,
) : RtAnnotationImplBase<ComponentFlavor>(impl), BuiltinAnnotation.ComponentFlavor, LexicalScope by lexicalScope {
    override val dimension: Type
        get() = RtTypeImpl(impl.dimension.java)

    companion object Factory : FactoryKey<ComponentFlavor, RtComponentFlavorAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtComponentFlavorAnnotationImpl
    }
}

internal class RtAssistedAnnotationImpl(
    impl: Assisted,
) : RtAnnotationImplBase<Assisted>(impl), BuiltinAnnotation.Assisted {
    override val value: String
        get() = impl.value
}
