/*
 * Copyright 2023 Yandex LLC
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
 * A boolean expression in terms of [Conditional] system. The expression consists of operations on boolean variables
 * (conditions) and is evaluated at runtime.
 *
 * ### Features
 * A *feature* is an annotation class (`@interface`) declaration, annotated with [ConditionExpression].
 * Features are supplied into [Conditional] annotations and may be referenced in other [ConditionExpression]s.
 *
 * See [value] on detailed info on how to write expressions.
 *
 * ### Quick examples
 * - `@ConditionExpression("!@MyFeature", MyFeature::class)` - negate the other feature.
 * - `@ConditionExpression("FEATURE1.isEnabled & !FEATURE2.isEnabled", Features::class)` - single import, so the
 *  short form is used twice - `FEATURE1` and `FEATURE2` are evaluated on the `Features` class.
 * - `@ConditionExpression("(isEnabledA | isEnabledB) & (isEnabledC | isEnabledD)", MyFeatureHelper::class)` -
 *  complex expression.
 *  - `@ConditionExpression("Helper::isEnabledA | @IsDebug | Features::FEATURE1.isEnabled", Features::class,
 *  IsDebug::class, Helper::class)` - qualified usages.
 *  - `@ConditionExpression("X::isEnabledA | X::isEnabledB & X::isRelease",
 *  importAs = [ImportAs(MyLongConditionsProviderName::class, "X")])` - [aliased import][importAs].
 *
 * ### Variable equality and caching
 *
 * Each boolean variable is computed only once per *its scope* and cached inside the corresponding component object.
 * The caching scope is the topmost component where the condition is used. This is done to prevent discrepancies if
 * the condition provider may return different results in different time.
 *
 * Variables in different [ConditionExpression]s are considered equal for caching purposes if and only if their resolved
 * *condition providers* and their *access paths* are equal correspondingly.
 * See [value] on the syntactic definitions.
 *
 * The moment where each variable is computed is currently not strictly defined. The framework may decide to compute
 * the condition as early as component instance initialization, or it may compute it directly before the first usage.
 */
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class ConditionExpression(
    /**
     * A boolean expression string, that is interpreted by the framework.
     *
     * ### Expression
     * Logical `&` (AND), `|` (OR), `!`(NOT) and `(`, `)` (parentheses) are supported freely.
     * The boolean variables (conditions) are written as follows:
     * 1. Short (unqualified) form: `<m1>.<m2>. ... <mn>` - consists of just an _access path_ - class members,
     *  separated by the dot `.`. The first _path_ member is evaluated on the _condition provider_,
     *  which is assumed to be the only class present in the [imports] list.
     *  E.g `@ConditionExpression("MY_FEATURE.get.isEnabled", MyConditions::class)`.
     * 2. Qualified form, e.g. `<condition-provider>::<m1>.<m2>. ... <mn>` - consists of a qualifier before the `::`
     *  sequence. After the `::` is an _access path_, as in short form. The qualifier name must be [imported][imports].
     *  E.g `@ConditionExpression("MyConditions::MY_FEATURE.get.isEnabled", MyConditions::class)`.
     *
     * The short form is only applicable, if there's exactly one import in the `@ConditionExpression` - then there's no
     * ambiguity in resolving the condition provider for the _access path_ specified.
     * If there are more than one imports - qualified form must be used.
     *
     * Besides variables, condition expression can reference other _features_ - using the "at" syntax:
     * `@<imported-feature-name>`, e.g. `@ConditionExpression("@MyFeature", MyFeature::class)`.
     * This way the entire expression from the `@MyFeature` is embedded into the referencing one.
     *
     * ### Access path
     *
     * Each identifier in chain represents a non-private field, method, resolved on a
     * resulting type of previous identifier or the root class if this is the first identifier.
     * The last identifier must resolve into a primitive boolean result.
     *
     * If the first member is static, then the resulting condition is a *plain (static) condition* - it can be evaluated
     * anywhere from static context.
     * If the first member is non-static, then, naturally, an instance of the imported class is required to compute
     * the condition. The instance is resolved *as a regular binding dependency*. Thus, it can be provided in any way
     * as a normal graph node could be - inject constructor, provision, binds, etc.
     *
     * Names are considered from Java point of view, not Kotlin! So property names are not a thing, getters should be
     * used instead; Kotlin objects are accessed with `.INSTANCE' or `.Companion`.
     *
     * ### Examples
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
     * @ConditionExpression("INSTANCE.isEnabled", SomeObject::class)
     * annotation class A
     *
     * // Static function from a class:
     * @ConditionExpression("staticComputeCondition", SomeClass::class)
     * annotation class B
     *
     * // Calls can be chained. Properties are accessible through explicit getters:
     * @ConditionExpression("staticGetSubObject.getMemberCondition", SomeClass::class)
     * annotation class C
     *
     * // Companion object must be mentioned explicitly:
     * @ConditionExpression("Companion.getProp", WithCompanion::class)
     * annotation class D
     *
     * // Non-static condition - an injectable class with non-static member:
     * @ConditionExpression("!getMemberCondition", ConditionProviderWithInject::class)
     * annotation class E
     * ```
     */
    val value: String,

    /**
     * List of imported classes. The simple name of every class here becomes accessible in the expression.
     * Then it can be used as either _condition provider_ or _feature reference_.
     *
     * Conflicting/duplicated imports are not allowed. If one wishes to import two classes with the same simple name
     * or just shorten the long class name to avoid writing it multiple times,
     * one can use an [aliased import][importAs].
     *
     * @see value
     */
    vararg val imports: KClass<*>,

    /**
     * Works the same way as [imports], but allows to import a given class by a specified name.
     *
     * @see value
     */
    val importAs: Array<ImportAs> = [],
) {
    /**
     * An import alias directive, used in [ConditionExpression.importAs].
     */
    @ConditionsApi
    @MustBeDocumented
    @Target(/*empty*/)
    @Retention(AnnotationRetention.RUNTIME)
    public annotation class ImportAs (
        /**
         * A class to import.
         */
        val value: KClass<*>,

        /**
         * A name to import [value] as. Must match the `[a-zA-Z_]+` regex.
         */
        val alias: String,
    )
}
