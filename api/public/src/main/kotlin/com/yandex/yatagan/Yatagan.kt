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

import com.yandex.yatagan.Yatagan.autoBuilder
import com.yandex.yatagan.Yatagan.builder
import com.yandex.yatagan.Yatagan.create
import com.yandex.yatagan.internal.DefaultImplementationLoader
import com.yandex.yatagan.internal.ImplementationLoader
import com.yandex.yatagan.internal.YataganInternal

/**
 * Yatagan entry-point object. Create instances of Yatagan components by loading generated implementations for
 * the given components/builders classes.
 *
 * Use either [builder],[autoBuilder] or [create].
 */
@OptIn(YataganInternal::class)
public object Yatagan {
    private const val DYNAMIC_LOADER_DELEGATE_CLASS_NAME = "com.yandex.yatagan.dynamic.ReflectionLoader"

    private val loaderForCompiled: ImplementationLoader = DefaultImplementationLoader()
    private val loaderForReflection: ImplementationLoader? = try {
        val dynamicLoaderClass = javaClass.classLoader.loadClass(DYNAMIC_LOADER_DELEGATE_CLASS_NAME)
        try {
            dynamicLoaderClass.getConstructor().newInstance() as ImplementationLoader
        } catch (e: Exception) {
            throw RuntimeException("Reflection backend detected, however was unable to load it. " +
                    "Check runtime classpath for consistency and/or Yatagan version mismatch.", e)
        }
    } catch (_: ClassNotFoundException) {
        // Reflection backend is not detected
        null
    }

    /**
     * [ThreadAsserter] object to be used in Single Thread component implementations.
     */
    @Volatile
    @JvmStatic
    public var threadAsserter: ThreadAsserter? = null

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
        return loadImpl { builder(builderClass) }
    }

    /**
     * Use this to create an "auto"-builder for components, that do not declare an explicit [Component.Builder].
     *
     * @see AutoBuilder
     */
    @JvmStatic
    public fun <T : Any> autoBuilder(componentClass: Class<T>): AutoBuilder<T> {
        return loadImpl { autoBuilder(componentClass) }
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

    private inline fun <T> loadImpl(load: ImplementationLoader.() -> Result<T>): T {
        return loaderForReflection?.let { reflection ->
            // Always try to load generated and compiled implementation first.
            // REASON: Libraries, that use Yatagan internally and ship with generated implementations
            // (and doesn't do any shadowing/repackaging/bytecode rewriting) don't want their internal components
            // to start using reflection if application developers use reflection.
            loaderForCompiled.load().getOrElse {
                // If generated implementation is not available - use reflection implementation.
                reflection.load().getOrThrow()
            }
        } ?: loaderForCompiled.load().getOrThrow()
    }
}