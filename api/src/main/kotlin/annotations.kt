@file:Suppress("DEPRECATED_JAVA_ANNOTATION")  // TODO: Replace with @JvmRepeatable when Kotlin 1.6 hits release.

package com.yandex.daggerlite

import java.lang.annotation.Repeatable
import kotlin.reflect.KClass

/*
 * TODO: documentation.
 */

// region Core API

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Module(
    val includes: Array<KClass<*>> = [],
    val subcomponents: Array<KClass<*>> = [],
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Binds(
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class Provides(
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Component(
    val isRoot: Boolean = true,
    val modules: Array<KClass<*>> = [],
    val dependencies: Array<KClass<*>> = [],
    val variant: Array<KClass<*>> = [],
) {
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class Factory
}

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class BindsInstance(
)

// endregion Core API

// region Conditions API

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Repeatable(AllConditions::class)
annotation class Condition(
    val value: KClass<*>,
    val condition: String,
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AllConditions(
    val value: Array<Condition>,
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Repeatable(AnyConditions::class)
annotation class AnyCondition(
    val value: Array<Condition>
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AnyConditions(
    val value: Array<AnyCondition>,
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditionals(
    val value: Array<Conditional>,
)

@MustBeDocumented
@Repeatable(Conditionals::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditional(
    val value: Array<KClass<out Annotation>> = [],
    val onlyIn: Array<KClass<*>> = [],
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ComponentVariantDimension

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ComponentFlavor(
    val dimension: KClass<out Annotation>,
)

// endregion Conditions API
