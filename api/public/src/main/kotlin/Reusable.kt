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

import javax.inject.Scope

/**
 * `Reusable` is a special [scope][Scope].
 * It declares, that a class wants to be cached *as a performance optimization** (e.g. because of a heavy constructor)
 * but doesn't want to be bound to any specific named scope.
 * It can only be placed on a binding (must be a single scope there) and not on
 * a component.
 *
 * In other words, `Reusable` has the following specific behavior (in additional to being a normal scope annotation):
 * 1) This scope is compatible with any component (with any scope or without one at all)
 * 2) *The caching doesn't guarantee to create a single instance in contended multi-thread environments in
 * [MT][Component.multiThreadAccess] components - single check caching is used instead of double check.
 */
@Scope
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CLASS,
)
public annotation class Reusable