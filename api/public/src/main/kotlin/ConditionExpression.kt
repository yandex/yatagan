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
 * A boolean expression in terms of [Conditional] system.
 * This API supersedes the old [Condition]/[AnyCondition] API based on repeatable annotations and
 * allows specifying an entire boolean expression using a single annotation and in a much more readable form.
 *
 * See [value] on detailed info on how to write expressions.
 *
 * Can not be used together with [Condition]/[AnyCondition] on the same _feature_,
 * however different features are compatible with each other without regarding the API used.
 *
 * Examples:
 * - `@ConditionExpression("!@MyFeature", MyFeature::class)` - negate the other feature.
 * - `@ConditionExpression("FEATURE1.isEnabled & !FEATURE2.isEnabled", Features::class)` - single import, so the
 *  short form is used twice - `FEATURE1` and `FEATURE2` are evaluated on the `Features` class.
 * - `@ConditionExpression("(isEnabledA | isEnabledB) & (isEnabledC | isEnabledD)", MyFeatureHelper::class)` -
 *  complex expression.
 *  - `@ConditionExpression("Helper::isEnabledA | @IsDebug | Features::FEATURE1.isEnabled", Features::class,
 *  IsDebug::class, Helper::class)` - qualified usages.
 *  - `@ConditionExpression("X::isEnabledA | X::isEnabledB & X::isRelease",
 *  importAs = [ImportAs(MyLongConditionsProviderName::class, "X")])` - [aliased import][importAs].
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
     * _Access path_ is interpreted in the same way, as in [Condition.value].
     */
    val value: String,

    /**
     * List of imported classes. The simple name of every class here becomes accessible in the expression.
     * Then it can be used as either _condition provider_ or _feature reference_.
     *
     * Conflicting/duplicated imports are not allowed. If one wishes to import two classes with the same simple name
     * or just shorten the long class name to avoid writing it multiple times,
     * one can use an [aliased import][importAs].
     */
    vararg val imports: KClass<*>,

    /**
     * Works the same way as [imports], but allows to import a given class by a specified name.
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
