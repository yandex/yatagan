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

/**
 *
 * Helps to just declare a multi-bound list to a type in case *there's no actual multi-bindings to that type*, so
 * that Yatagan won't complain with about a missing binding. If no bindings are present for a list, then an empty list
 * is provided.
 *
 * Example:
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.yatagan.*
 * @Module
 * interface SomeModule {
 *   @Multibinds
 *   fun listOfNumbers(): List<Number>
 *
 *   @Multibinds
 *   fun mapOfIntToString(): Map<Int, String>
 * }
 *
 * // Possible component declaration:
 *
 * @Component(modules = [SomeModule::class])
 * interface ExampleComponent {
 *   val numbers: List<Number>
 *   val map: Map<Int, String>
 * }
 *
 * // Then the following holds true:
 *
 * /*@*/ fun test() {
 * Yatagan.create(ExampleComponent::class.java).run {
 * /*@*/assert(
 *     numbers.isEmpty()
 * /*@*/ &&
 *     map.isEmpty()
 * /*@*/)
 * }
 * /*@*/}
 * ```
 *
 * @see IntoList
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class Multibinds(
)