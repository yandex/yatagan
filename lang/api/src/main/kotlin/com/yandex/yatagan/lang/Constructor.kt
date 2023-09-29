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

import com.yandex.yatagan.base.api.Internal

/**
 * Models a type constructor.
 */
public interface Constructor : Callable, Annotated, Accessible {
    /**
     * An owner and a constructed type of the constructor.
     */
    public val constructee: TypeDeclaration

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnConstructor> getAnnotation(
        which: BuiltinAnnotation.Target.OnConstructor<T>
    ): T?
}