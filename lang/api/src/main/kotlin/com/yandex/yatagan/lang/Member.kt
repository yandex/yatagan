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
 * Models a type declaration member.
 */
public interface Member : Annotated, HasPlatformModel, Accessible {
    /**
     * Type declaration that this entity is member of.
     */
    public val owner: TypeDeclaration

    /**
     * Whether the member is truly static (@[JvmStatic] or `static`).
     */
    public val isStatic: Boolean

    /**
     * Member name.
     */
    public val name: String

    @StableForImplementation
    public interface Visitor<R> {
        public fun visitOther(model: Member): R
        public fun visitMethod(model: Method): R = visitOther(model)
        public fun visitField(model: Field): R = visitOther(model)
    }

    public fun <R> accept(visitor: Visitor<R>): R
}