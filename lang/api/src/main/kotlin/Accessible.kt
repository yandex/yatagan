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

/**
 * A trait for models that encodes a language construct's accessibility.
 */
public interface Accessible {
    /**
     * `true` is the entity is practically accessible from any package or module. `false` otherwise.
     *
     * NOTE: Kotlin's `internal` is presumed accessible, as it compiles into Java's `public` and technically can be
     * accessed from another module by its mangled name. Mangled name can change across compilation configurations, yet
     * it's fine with code generation and/or reflection.
     */
    public val isEffectivelyPublic: Boolean
}