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
 * Denotes type declaration kind.
 */
public enum class TypeDeclarationKind {
    /**
     * No declaration is logically present for the type.
     * Arrays, primitive types, void, etc.
     */
    None,

    /**
     * `class` declaration.
     */
    Class,

    /**
     * `enum` class declaration.
     */
    Enum,

    /**
     * `interface` declaration.
     */
    Interface,

    /**
     * `@interface`/`annotation class` declaration.
     */
    Annotation,

    /**
     * Kotlin-specific: `object` declaration.
     */
    KotlinObject,

    /**
     * Kotlin-specific `companion object` declaration.
     *
     * NOTE: only companions with default name `Companion` are recognized due to compatibility reasons.
     */
    KotlinCompanion,
}