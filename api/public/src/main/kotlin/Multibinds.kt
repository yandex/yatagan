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