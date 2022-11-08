package com.yandex.daggerlite

import kotlin.reflect.KClass

/**
 * Specifies that an associated entity (a class with [javax.inject.Inject] constructor/a [provision][Provides]/
 * a [child component][Module.subcomponents])
 * should be present in a graph **under certain runtime [conditions][Condition]**.
 *
 * Types under condition are permitted to be directly injected only into the types
 *  under the same or *implying* condition.
 * **The condition implication checking is enforced by the framework** at graph building time.
 * If a client is not under condition or under different condition, then [Optional] dependency is required to be able
 * to inject a conditional type.
 *
 * ```kotlin
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 **
 * /*@*/object Flags {
 * /*@*/ var flagA: Boolean = true
 * /*@*/ var flagB: Boolean = false
 * /*@*/}
 *
 * // Assume we have the following features declared:
 *
 * @Condition(Flags::class, "flagA") annotation class FeatureA
 * @Condition(Flags::class, "flagB") annotation class FeatureB
 * @AllConditions(
 *  Condition(Flags::class, "flagA"),
 *  Condition(Flags::class, "flagB"),
 * )
 * annotation class FeatureAB
 *
 * // Then for a class under `A` it's only allowed to inject class under `B` wrapped in `Optional`:
 * @Conditional(FeatureA::class)
 * class UnderA @Inject constructor(
 *   val b: Optional<UnderB>
 * )
 *
 * // And so goes for an `A` from `B`:
 * @Conditional(FeatureB::class)
 * class UnderB @Inject constructor(
 *   val a: Optional<Provider<UnderA>>
 * )
 *
 * // Yet it's okay to inject `UnderA`, `UnderB` into a class under `A && B` feature,
 * // because `A && B` implies both `A` and `B`, so no condition violation is possible.
 * @Conditional(FeatureAB::class)
 * class UnderAB @Inject constructor(
 *   val a: UnderA,
 *   val b: UnderB,
 * )
 * ```
 *
 * @see value
 * @see onlyIn
 * @see Binds
 * @see Provides.value
 */
@ConditionsApi
@MustBeDocumented
@JvmRepeatable(Conditionals::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditional(
    /**
     * A list of **feature declarations** - annotation types, annotated with
     * [Condition]-family annotations to form an expression.
     *
     * The features in this list are `&&`-ed together.
     */
    vararg val value: KClass<out Annotation> = [],

    /**
     * Component flavor constraint within *Variant API*,
     * which constitutes a compile-time condition.
     *
     * A list of [ComponentFlavor]-annotated annotation types.
     *
     * ## Examples:
     * ```kotlin
     * /*@*/ import com.yandex.daggerlite.*
     * /*@*/ import javax.inject.*
     *
     * /*@*/ object Flags { var a = false; var b = false; var c = false  }
     * /*@*/ @Condition(Flags::class, "a") annotation class FeatureA
     * /*@*/ @Condition(Flags::class, "b") annotation class FeatureB
     * /*@*/ @Condition(Flags::class, "c") annotation class FeatureC
     *
     * // Assume the following *flavors* and *dimensions* are declared:
     * @ComponentVariantDimension annotation class Product {
     *   @ComponentFlavor(dimension = Product::class) annotation class FooApp
     *   @ComponentFlavor(dimension = Product::class) annotation class BarApp
     * }
     * @ComponentVariantDimension annotation class Device {
     *   @ComponentFlavor(dimension = Device::class) annotation class Tablet
     *   @ComponentFlavor(dimension = Device::class) annotation class Phone
     *   @ComponentFlavor(dimension = Device::class) annotation class Watch
     * }
     *
     * // Simple runtime condition - entity is accessible in every component under FeatureA:
     * @Conditional(FeatureA::class)
     * class UnderFeatureA @Inject constructor()
     *
     * // Simple compile-time filter - entity is accessible only in components, that declare Device.Watch flavor:
     * @Conditional(onlyIn = [Device.Watch::class])
     * class WatchSpecific @Inject constructor()
     *
     * // Entity is accessible only in components that declare `Device.Phone` in their [variant][Component.variant].
     * // Inaccessible anywhere else:
     * @Conditional(FeatureA::class, onlyIn = [Device.Phone::class])
     * class PhoneSpecificUnderFeatureA @Inject constructor()
     *
     * // More complex example with multiple conditionals:
     * @Conditional(FeatureA::class, FeatureB::class, onlyIn = [
     *   Product.FooApp::class,
     *   Device.Phone::class, Device.Tablet::class,
     * ])  // accessible in FooApp on phones and tablets (but not on watches) under FeatureA && FeatureB.
     * @Conditional(FeatureC::class, onlyIn = [
     *   Product.BarApp::class
     * ])  // accessible in BarApp (in all form-factors) under FeatureC.
     * class Complex @Inject constructor()
     * ```
     *
     * @see Component.variant
     * @see ComponentFlavor
     * @see ComponentVariantDimension
     */
    val onlyIn: Array<KClass<*>> = [],
)