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

/**
 * An interface for universal (aka "auto") component creator interface. Implemented by the framework for
 * [root][Component.isRoot] components with no explicit [creator][Component.Builder] interface declared.
 *
 * This version of a component creator is not type-safe and should be used with caution. Declaring a custom builder
 * for a component is always a safer option, so do that where possible.
 *
 * @param T created component type
 */
public interface AutoBuilder<out T> {
    /**
     * Supplies an input into the builder.
     *
     * @throws IllegalArgumentException if the input is unexpected (not required).
     *
     * @param input a module/dependency instance required for component creation.
     * @param clazz a class of the input to exactly match one of the classes declared in
     *  [Component.modules]/[Component.dependencies].
     *
     * @return this
     */
    public fun <I : Any> provideInput(input: I, clazz: Class<I>): AutoBuilder<T>

    /**
     * Supplies an input into the builder.
     *
     * The input's runtime class should exactly match one of the classes declared in [Component.modules] or
     * [Component.dependencies], otherwise the builder won't be able to recognize the input. If the input's class is a
     * subclass of the one declared in the component, use the [overload][provideInput] with the explicit `clazz`
     * argument.
     *
     * @throws IllegalArgumentException if the input is unexpected (not required).
     *
     * @param input a module/dependency required for component creation.
     *
     * @return this
     *
     * @see provideInput
     */
    public fun provideInput(input: Any): AutoBuilder<T> {
        return provideInput(input, clazz = input.javaClass)
    }

    /**
     * Creates a new component instance.
     *
     * @throws IllegalStateException if some required inputs are missing.
     *
     * @return created component instance.
     */
    public fun create(): T
}
