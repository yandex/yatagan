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

/**
 * See Dagger [docs](https://dagger.dev/api/latest/dagger/Provides.html).
 *
 * If it's necessary to create optional bindings, use either [Conditional], or custom Optional-like wrapper types.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
)
@OptIn(ConditionsApi::class)
public annotation class Provides(
    /**
     * One or more [Conditional] specifiers for this provision.
     */
    vararg val value: Conditional = [],
)