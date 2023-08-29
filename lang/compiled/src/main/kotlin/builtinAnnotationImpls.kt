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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type

internal abstract class CtBuiltinAnnotationBase(
    protected val impl: CtAnnotationBase
) {
    final override fun toString() = impl.toString()
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?) = this === other || (other is CtBuiltinAnnotationBase && impl == other.impl)
}

internal class CtComponentAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Component {
    override val isRoot get() = impl.getBoolean("isRoot")
    override val modules get() = impl.getTypes("modules")
    override val dependencies get() = impl.getTypes("dependencies")
    override val variant get() = impl.getTypes("variant")
    override val multiThreadAccess get() = impl.getBoolean("multiThreadAccess")
}

internal class CtModuleAnnotationImpl(
    impl: CtAnnotationBase
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Module {
    override val includes get() = impl.getTypes("includes")
    override val subcomponents get() = impl.getTypes("subcomponents")
}

internal class CtConditionAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.ConditionFamily.One {
    override val target get() = impl.getType("value")
    override val condition get() = impl.getString("condition")
}

internal class CtAnyConditionAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.ConditionFamily.Any {
    override val conditions get() = impl.getAnnotations("value").map { CtConditionAnnotationImpl(it) }
}

internal class CtConditionExpressionAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.ConditionExpression {
    override val value: String
        get() = impl.getString("value")
    override val imports: List<Type>
        get() = impl.getTypes("imports")
    override val importAs: List<BuiltinAnnotation.ConditionExpression.ImportAs> by lazy {
        impl.getAnnotations("importAs").map { ImportAsImpl(it) }
    }

    private data class ImportAsImpl(
        private val impl: CtAnnotationBase,
    ) : BuiltinAnnotation.ConditionExpression.ImportAs {
        override val value: Type = impl.getType("value")
        override val alias: String = impl.getString("alias")
    }
}

internal class CtConditionalAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Conditional {
    override val featureTypes get() = impl.getTypes("value")
    override val onlyIn get() = impl.getTypes("onlyIn")
}

internal class CtComponentFlavorAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.ComponentFlavor {
    override val dimension get() = impl.getType("dimension")
}

internal class CtProvidesAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Provides {
    override val conditionals get() = impl.getAnnotations("value").map { CtConditionalAnnotationImpl(it) }
}

internal class CtIntoListAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoList {
    override val flatten get() = impl.getBoolean("flatten")
}

internal class CtIntoSetAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
    override val flatten get() = impl.getBoolean("flatten")
}

internal class CtAssistedAnnotationImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Assisted {
    override val value get() = impl.getString("value")
}