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

import kotlin.reflect.KClass

/**
 * See Dagger [docs](https://dagger.dev/api/latest/dagger/Module.html).
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class Module(
    /**
     * Additional [Modules][Module] to be transitively included into a [Component]/another [Module].
     * Allows duplicates, recursively.
     */
    val includes: Array<KClass<*>> = [],

    /**
     * [Component]-annotated interfaces, that should be children in a [Component] which includes this
     * module.
     * Duplicates are allowed, recursively via [includes].
     * Any included [components][Component] must have [Component.isRoot] to be set to `false`.
     *
     * This is the only way to declare child components.
     */
    val subcomponents: Array<KClass<*>> = [],
)