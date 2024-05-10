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

package com.yandex.yatagan

import kotlin.reflect.KClass

/**
 * Specifies that an associated entity (a class with [javax.inject.Inject] constructor/a [provision][Provides]/
 * a [child component][Module.subcomponents])
 * should be present in a graph **under certain runtime [conditions][ConditionExpression]**.
 *
 * Types under condition are permitted to be directly injected only into the types
 *  under the same or *implying* condition.
 * **The condition implication checking is enforced by the framework** at graph building time.
 * If a client is not under condition or under different condition, then [Optional] dependency is required to be able
 * to inject a conditional type.
 *
 * ```kotlin
 * object Flags {
 *  var flagA: Boolean = true
 *  var flagB: Boolean = false
 * }
 *
 * // Assume we have the following features declared:
 *
 * @ConditionExpression("flagA", Flags::class) annotation class FeatureA
 * @ConditionExpression("flagB", Flags::class) annotation class FeatureB
 * @ConditionExpression("flagA & flagB", Flags::class) annotation class FeatureAB
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
 * @see Provides
 * @see ConditionExpression
 */
@ConditionsApi
@MustBeDocumented
@JvmRepeatable(Conditionals::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class Conditional(
    /**
     * A list of **feature declarations** - annotation types, annotated with [ConditionExpression].
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
     * object Flags { var a = false; var b = false; var c = false  }
     * @ConditionExpression("a", Flags::class) annotation class FeatureA
     * @ConditionExpression("b", Flags::class) annotation class FeatureB
     * @ConditionExpression("c", Flags::class) annotation class FeatureC
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