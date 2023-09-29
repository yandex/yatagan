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

import javax.inject.Qualifier

/**
 * A special [Qualifier], that instructs framework to inject a boolean condition, that [value] evaluates to.
 * Must only be used on `boolean` dependencies. [javax.inject.Provider] usage is allowed to delay condition evaluation.
 * [Lazy]/[Optional] usage is technically allowed, but is strongly discouraged -
 * they don't make sense for condition values.
 *
 * This may be preferable to the direct manual condition evaluation because the injected value is cached inside
 *  the component and is guaranteed to be consistent with the [Conditional]s. So if condition computation is for some
 *  reason expensive, the caching behavior may be of help in that case.
 * Besides that, it may be convenient to use this for testing purposes - it's then easier to write parameterized tests.
 *
 * @see ConditionExpression
 */
@Qualifier
@ConditionsApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class ValueOf(
    val value: ConditionExpression,
)
