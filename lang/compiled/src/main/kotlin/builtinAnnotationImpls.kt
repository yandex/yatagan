package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.BuiltinAnnotation

internal abstract class CtAnnotationBase(
    protected val impl: CtAnnotation
) {
    final override fun toString() = impl.toString()
    final override fun hashCode() = impl.hashCode()
    final override fun equals(other: Any?) = this === other || (other is CtAnnotationBase && impl == other.impl)
}

internal class CtComponentAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.Component {
    override val isRoot get() = impl.getBoolean("isRoot")
    override val modules get() = impl.getTypes("modules")
    override val dependencies get() = impl.getTypes("dependencies")
    override val variant get() = impl.getTypes("variant")
    override val multiThreadAccess get() = impl.getBoolean("multiThreadAccess")
}

internal class CtModuleAnnotationImpl(
    impl: CtAnnotation
) : CtAnnotationBase(impl), BuiltinAnnotation.Module {
    override val includes get() = impl.getTypes("includes")
    override val subcomponents get() = impl.getTypes("subcomponents")
}

internal class CtConditionAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.ConditionFamily.One {
    override val target get() = impl.getType("value")
    override val condition get() = impl.getString("condition")
}

internal class CtAnyConditionAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.ConditionFamily.Any {
    override val conditions get() = impl.getAnnotations("value").map { CtConditionAnnotationImpl(it) }
}

internal class CtConditionalAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.Conditional {
    override val featureTypes get() = impl.getTypes("value")
    override val onlyIn get() = impl.getTypes("onlyIn")
}

internal class CtComponentFlavorAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.ComponentFlavor {
    override val dimension get() = impl.getType("dimension")
}

internal class CtProvidesAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.Provides {
    override val conditionals get() = impl.getAnnotations("value").map { CtConditionalAnnotationImpl(it) }
}

internal class CtIntoListAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoList {
    override val flatten get() = impl.getBoolean("flatten")
}

internal class CtIntoSetAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
    override val flatten get() = impl.getBoolean("flatten")
}

internal class CtAssistedAnnotationImpl(
    impl: CtAnnotation,
) : CtAnnotationBase(impl), BuiltinAnnotation.Assisted {
    override val value get() = impl.getString("value")
}