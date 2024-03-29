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

package com.yandex.yatagan.lang

import com.yandex.yatagan.base.api.StableForImplementation

/**
 * A sealed marker interface for an entity that can be called.
 */
public interface Callable : HasPlatformModel {

    /**
     * Parameters required to call this callable.
     */
    public val parameters: Sequence<Parameter>

    public fun <T> accept(visitor: Visitor<T>): T

    @StableForImplementation
    public interface Visitor<T> {
        public fun visitOther(callable: Callable): T
        public fun visitMethod(method: Method): T = visitOther(method)
        public fun visitConstructor(constructor: Constructor): T = visitOther(constructor)
    }
}