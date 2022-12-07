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
 * A modifier annotation, which behaves largely like [IntoList] with the following differences:
 *
 * - Binds `java.util.Set<[? extends] T>` instead of `List`.
 * - The order of contributions inside the resulting set *is not defined* in any way.
 * - No duplicates (as per `Set` contract) could be present in the set. E.g. if any two `@Provides` return the same
 *  instance/instance that compare equals via `equals` - there'll be only one of them in the set.
 */
public annotation class IntoSet(
    /**
     * Same as [IntoList.flatten].
     */
    val flatten: Boolean = false,
)