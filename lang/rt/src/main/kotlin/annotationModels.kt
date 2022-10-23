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
import com.yandex.daggerlite.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.lang.IntoCollectionAnnotationLangModel
import com.yandex.daggerlite.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.lang.ProvidesAnnotationLangModel
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
) : RtAnnotationImplBase<Component>(impl), ComponentAnnotationLangModel {
    override val isRoot: Boolean get() = impl.isRoot
    override val modules: Sequence<Type> = impl.modules.asSequence().map { RtTypeImpl(it.java) }
    override val dependencies: Sequence<Type> = impl.dependencies.asSequence().map { RtTypeImpl(it.java) }
    override val variant: Sequence<Type> = impl.variant.asSequence().map { RtTypeImpl(it.java) }
    override val multiThreadAccess: Boolean get() = impl.multiThreadAccess
}

internal class RtModuleAnnotationImpl(
    impl: Module,
) : RtAnnotationImplBase<Module>(impl), ModuleAnnotationLangModel {
    override val includes: Sequence<Type>
        get() = impl.includes.asSequence().map { RtTypeImpl(it.java) }
    override val subcomponents: Sequence<Type>
        get() = impl.subcomponents.asSequence().map { RtTypeImpl(it.java) }
}

internal class RtConditionalAnnotationImpl(
    impl: Conditional,
) : RtAnnotationImplBase<Conditional>(impl), ConditionalAnnotationLangModel {
    override val featureTypes: Sequence<Type> = impl.value.asSequence().map { RtTypeImpl(it.java) }
    override val onlyIn: Sequence<Type> = impl.onlyIn.asSequence().map { RtTypeImpl(it.java) }
}

internal class RtProvidesAnnotationImpl(
    impl: Provides,
) : RtAnnotationImplBase<Provides>(impl), ProvidesAnnotationLangModel {
    override val conditionals: Sequence<ConditionalAnnotationLangModel> = impl.value.asSequence().map {
        RtConditionalAnnotationImpl(it)
    }
}

internal class RtIntoListAnnotationImpl(
    impl: IntoList,
) : RtAnnotationImplBase<IntoList>(impl), IntoCollectionAnnotationLangModel {
    override val flatten: Boolean
        get() = impl.flatten
}

internal class RtIntoSetAnnotationImpl(
    impl: IntoSet,
) : RtAnnotationImplBase<IntoSet>(impl), IntoCollectionAnnotationLangModel {
    override val flatten: Boolean
        get() = impl.flatten
}

internal class RtConditionAnnotationImpl(
    impl: Condition,
) : RtAnnotationImplBase<Condition>(impl), ConditionAnnotationLangModel {
    override val target: Type
        get() = RtTypeImpl(impl.value.java)
    override val condition: String
        get() = impl.condition
}

internal class RtAnyConditionAnnotationImpl(
    impl: AnyCondition,
) : RtAnnotationImplBase<AnyCondition>(impl), AnyConditionAnnotationLangModel {
    override val conditions: Sequence<ConditionAnnotationLangModel> = impl.value.asSequence().map {
        RtConditionAnnotationImpl(it)
    }
}

internal class RtComponentFlavorAnnotationImpl(
    impl: ComponentFlavor,
) : RtAnnotationImplBase<ComponentFlavor>(impl), ComponentFlavorAnnotationLangModel {
    override val dimension: Type
        get() = RtTypeImpl(impl.dimension.java)
}

internal class RtAssistedAnnotationImpl(
    impl: Assisted,
) : RtAnnotationImplBase<Assisted>(impl), AssistedAnnotationLangModel {
    override val value: String
        get() = impl.value
}
