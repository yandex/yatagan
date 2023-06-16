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

package com.yandex.yatagan.base.api

/**
 * Internal model API marker. No guarantees for binary, source or behavioral compatibility.
 * It's present in the public API for only technical reasons.
 * Best no to depend on such API outside the framework at all.
 */
@RequiresOptIn(
    message = "This API is internal to the framework and is not intended to be used by the clients",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
public annotation class Internal
