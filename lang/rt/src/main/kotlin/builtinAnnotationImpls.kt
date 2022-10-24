@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.ConditionsApi
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.VariantApi
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.Type

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
