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
 * Works almost the same [IntoSet] with the following differences:
 *
 * A special modifier annotation that can be applied together with [Binds] or [Provides].
 *
 * The order of the elements in the resulting list is defined as follows:
 * 1. The bound type is `List<(out) T>`, instead of a `Set`.
 * 2. the contributing bindings are sorted in a stable (though non-intuitive) way.
 * The order should be consistent across all backends and framework versions.
 * 3. the instances will be topologically-sorted according to their binding's dependencies.
 * 4. Duplicates, if returned from multiple provisions, will be, naturally, present in the list.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class IntoList(
    /**
     * If this is set to `true`, then multi-binding
     * treats return type as a collection, elements of which will all be contributed to the multi-bound list.
     *
     * The return type actually *must* be compatible with a `Collection` interface.
     *
     * See example [here][IntoList], that includes usage of this flag.
     */
    val flatten: Boolean = false,
)