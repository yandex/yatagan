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

package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.AnnotationDeclaration

/**
 * Represents a scope of a binding or a component.
 */
public interface ScopeModel {
    /**
     * Annotation class for the scope. `null` if the scope is intrinsic (e.g [Reusable]).
     */
    public val customAnnotationClass: AnnotationDeclaration?

    public companion object {
        /**
         * Reusable scope.
         */
        public val Reusable: ScopeModel = object : ScopeModel {
            override val customAnnotationClass: AnnotationDeclaration? get() = null
            override fun toString() = "@Reusable [builtin]"
        }
    }
}