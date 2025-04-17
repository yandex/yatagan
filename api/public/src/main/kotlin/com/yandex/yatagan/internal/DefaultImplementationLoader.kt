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

package com.yandex.yatagan.internal

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.Component
import java.lang.reflect.Method
import java.lang.reflect.Modifier

@YataganInternal
internal class DefaultImplementationLoader : ImplementationLoader {
    override fun <T : Any> builder(builderClass: Class<T>): Result<T> {
        return try {
            require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
                "$builderClass is not a builder for a Yatagan component"
            }
            val componentClassName = builderClass.name.substringBeforeLast("$")
            require(componentClassName != builderClass.name) {
                "No enclosing component class found for $builderClass"
            }
            val componentClass = builderClass.classLoader.loadClass(componentClassName)
            val yataganComponentClass = loadImplementationClass(componentClass)
            val builder = builderClass.cast(yataganComponentClass.getDeclaredMethod("builder").invoke(null))
            Result.success(builder)
        } catch (e: ClassNotFoundException) {
            Result.failure(e)
        }
    }

    override fun <T : Any> autoBuilder(componentClass: Class<T>): Result<AutoBuilder<T>> {
        return try {
            val yataganComponentClass = loadImplementationClass(componentClass)
            val autoBuilder = try {
                yataganComponentClass.getDeclaredMethod("autoBuilder")
            } catch (_: NoSuchMethodException) {
                try {
                    // Try to locate "create" method for code generated with <= 1.1.0
                    yataganComponentClass.getDeclaredMethod("create")
                        .takeIf { Modifier.isStatic(it.modifiers) }?.let { method ->
                            return Result.success(AutoBuilderForCreateCompat(
                                createMethod = method,
                                componentClass = componentClass,
                            ))
                        }
                } catch (_: NoSuchMethodException) {
                    // No, no `create` here.
                }

                throw IllegalArgumentException(
                    "Auto-builder can't be used for $componentClass, because it declares an explicit builder. " +
                            "Please use `Yatagan.builder()` instead"
                )
            }
            @Suppress("UNCHECKED_CAST")
            Result.success(autoBuilder.invoke(null) as AutoBuilder<T>)
        } catch (e: ClassNotFoundException) {
            Result.failure(e)
        }
    }

    private fun loadImplementationClass(componentClass: Class<*>): Class<*> {
        require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
            "$componentClass is not a root Yatagan component"
        }

        // Keep name mangling in sync with codegen!
        val (packageName, binaryName) = splitComponentName(componentClass)
        // no need to parse and join simple names, as codegen joins them with '$' and
        // that's what JVM binary class name already is.
        val implementationName = "$packageName.Yatagan${binaryName.replace('$', '_')}"

        return try {
            componentClass.classLoader.loadClass(implementationName)
        } catch (e1: ClassNotFoundException) {
            // fallback to the legacy loader name
            try {
                componentClass.classLoader.loadClass("$packageName.Yatagan\$$binaryName")
            } catch (e2: ClassNotFoundException) {
                throw e2.also { it.addSuppressed(e1) }
            }
        }
    }

    private fun splitComponentName(clazz: Class<*>): Pair<String, String> {
        val name = clazz.name
        return when(val lastDot = name.lastIndexOf('.')) {
            -1 -> "" to name
            else -> name.substring(0, lastDot) to name.substring(lastDot + 1)
        }
    }

    private class AutoBuilderForCreateCompat<T>(
        private val componentClass: Class<T>,
        private val createMethod: Method,
    ): AutoBuilder<T> {
        override fun create(): T = componentClass.cast(createMethod.invoke(null))
        override fun <I : Any> provideInput(input: I, clazz: Class<I>) =
            reportUnexpectedAutoBuilderInput(input.javaClass, emptyList())
    }
}