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
 * A boolean literal in terms of [Conditional] system.
 * Denotes a single boolean condition, which can be evaluated at runtime.
 *
 * This condition is fully opaque in terms of the framework and its correctness checks.
 * Conditions are considered equal by the [value] and parsed [condition].
 * If two conditions are not equal, then they are treated as
 * completely unrelated conditions and no guesses are further made.
 *
 * This annotation is repeatable forming up an [AllConditions] container - repeating
 * annotations are `AND`ed together.
 * To build an 'OR' condition use [AnyCondition] annotation, which is also repeatable and all
 * it's instances are `AND`ed together in [AnyConditions] container.
 * So in general case the resulting expression is a conjunction ('AND') of disjunctions ('OR') or,
 * in other words, Conjunctive Normal Form.
 *
 * The unique condition value is computed **only once** to avoid any inconsistencies at runtime and cached inside the
 * component object.
 * The computation may occur either at component creation or on demand when immediately required - this depends
 * on a condition usage. *Non-static* conditions are always computed on-demand.
 */
@Suppress("DEPRECATION")
@Deprecated("Legacy Conditions API, use @ConditionExpression instead")
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AllConditions::class)
public annotation class Condition(
    /**
     * A root class from which a condition accessor chain is built.
     */
    val value: KClass<*>,

    /**
     * A string consisting from plain Java identifiers, separated by '.' and optionally staring with '!'.
     *
     * Each identifier in chain represents a non-private field, method, resolved on a
     * resulting type of previous identifier or the root class if this is the first identifier.
     * The last identifier must resolve into a primitive boolean result.
     *
     * If the first member is static, then the resulting condition is a *plain (static) condition* - it can be evaluated
     * anywhere from static context.
     * If the first member is non-static, then, naturally, an instance of the [root][value] class is required to compute
     * the condition. The instance is resolved *as a regular binding dependency*. Thus, it can be provided in any way
     * as a normal graph node could be - inject constructor, provision, binds, etc.
     *
     * Names are considered from Java point of view, not Kotlin! So property names are not a thing, getters should be
     * used instead.
     *
     * If starts with '!', the result value will be negated.
     *
     * Examples:
     * ```kotlin
     * object SomeObject { val isEnabled = false }
     * object SomeClass {
     *   @JvmStatic fun staticComputeCondition() = false
     *   @JvmStatic fun staticGetSubObject() = AnotherClass()
     * }
     * class AnotherClass { val memberCondition = false }
     * class WithCompanion { companion object { val prop = false } }
     * class ConditionProviderWithInject @Inject constructor() { val memberCondition = true }
     *
     * // Kotlin's singletons require explicit INSTANCE identifier:
     * @Condition(SomeObject::class, "INSTANCE.isEnabled")
     * annotation class A
     *
     * // Static function from a class:
     * @Condition(SomeClass::class, "staticComputeCondition")
     * annotation class B
     *
     * // Calls can be chained. Properties are accessible through explicit getters:
     * @Condition(SomeClass::class, "staticGetSubObject.getMemberCondition")
     * annotation class C
     *
     * // Companion object must be mentioned explicitly:
     * @Condition(WithCompanion::class, "Companion.getProp")
     * annotation class D
     *
     * // Non-static condition - an injectable class with non-static member:
     * @Condition(ConditionProviderWithInject::class, "!getMemberCondition")
     * annotation class E
     * ```
     */
    val condition: String,
)