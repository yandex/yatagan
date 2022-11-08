package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.BuiltinAnnotation

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