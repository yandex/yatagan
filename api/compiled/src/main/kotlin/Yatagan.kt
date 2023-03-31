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

import com.yandex.yatagan.Yatagan.builder
import com.yandex.yatagan.Yatagan.create
import com.yandex.yatagan.common.loadAutoBuilderImplementationByComponentClass
import com.yandex.yatagan.common.loadImplementationByBuilderClass
import com.yandex.yatagan.internal.ThreadAssertions

/**
 * Yatagan entry-point object. Create instances of Yatagan components by loading generated implementations for
 * the given components/builders classes.
 *
 * Use either [builder] or [create].
 */
public object Yatagan {

    /**
     * Sets [ThreadAsserter] object to be used in Single Thread component implementations.
     */
    @JvmStatic
    public fun setThreadAsserter(threadAsserter: ThreadAsserter?) {
        ThreadAssertions.setAsserter(threadAsserter)
    }

    /**
     * Use this to create a component builder instance for root components that declare it.
     *
     * @param builderClass component builder class
     * @return ready component builder instance of the given class
     *
     * @see Component.Builder
     */
    @JvmStatic
    public fun <T : Any> builder(builderClass: Class<T>): T {
        return loadImplementationByBuilderClass(builderClass)
    }

    /**
     * Use this to create an "auto"-builder for components, that do not declare an explicit [Component.Builder].
     *
     * @see AutoBuilder
     */
    @JvmStatic
    public fun <T : Any> autoBuilder(componentClass: Class<T>): AutoBuilder<T> {
        return loadAutoBuilderImplementationByComponentClass(componentClass)
    }

    /**
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface and do not declare any [Component.dependencies] or
     * [Component.modules] that require instance.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    public fun<T : Any> create(componentClass: Class<T>): T {
        return autoBuilder(componentClass).create()
    }
}