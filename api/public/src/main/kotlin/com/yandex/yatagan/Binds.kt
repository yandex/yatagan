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
 * See Dagger [docs](https://dagger.dev/api/latest/dagger/Binds.html).
 *
 * In Yatagan, `Binds` can play a special role as a part of Condition API.
 * One can use _multiple alternatives_ for binds arguments:
 * ```kotlin
 * @Binds
 * fun alternatives(impl: Impl1, orThis: Impl2, fallback: Stub): Api
 * ```
 * This will bind `Impl1` to `Api` if it is present in the graph;
 * `Impl2` will be tried if `Impl1` is not present and ultimately `Stub` will be used if no previous alternatives
 * are present. If even `Stub` is under a condition itself, then the `Api` is also under a condition.
 *
 * One can use `@Binds` without an argument at all, like this:
 * ```kotlin
 * @Binds fun noApi(): Api
 * ```
 * This is an _explicitly absent_ binding, which declares that `Api` is always absent (under "never"-condition).
 *
 * @see Optional
 * @see ConditionExpression
 * @see Conditional
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class Binds(
)