package com.yandex.yatagan

/**
 * A special modifier annotation that can be applied together with [Binds] or [Provides].
 *
 * When applied, a *binding* becomes a *multi-binding* - a `java.util.List<[? extends] T>`is introduced to the graph,
 * where `T` is a return type of the annotated method.
 *
 * Therese can be multiple `IntoList` bindings for a given node (type + qualifier).
 * The list will contain *all* instances, provided by the bindings.
 *
 * The order of the elements in the resulting list is defined as follows:
 * 1. the contributing bindings are sorted in a stable (though non-intuitive) way.
 * The order should be consistent across all implementations and framework versions.
 * 2. the instances will be topologically-sorted according to their binding's dependencies.
 *
 * The multi-binding's return type does not "spill" outside the list and does not conflict with other non-multi
 * bindings for the same type.
 *
 * Let's assume we have the following bindings
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.yatagan.*
 * /*@*/ import javax.inject.*
 *
 * /*@*/@Module interface TestModule {
 * /*@*/ companion object {
 *
 * @IntoList
 * @Provides
 * fun provideOne(): Number = 1.1f
 *
 * @Provides
 * @Named("two")
 * fun someDouble(): Double = 2.0
 *
 * @IntoList(flatten = true)
 * @Provides
 * fun numbers(): Collection<Number> = listOf(3, -4L, 5)
 *
 * /*@*/}
 *
 * @IntoList
 * @Binds
 * fun bindTwo(@Named("two") d: Double): Number
 *
 * /*@*/}
 *
 * // Then the following entry-point may be declared inside the component:
 * /*@*/ @Component(modules = [TestModule::class]) interface ExampleComponent {
 * val numbers: List<Number>
 * /*@*/}
 *
 * // And the following will hold true for the list:
 *
 * /*@*/ fun test() {
 * /*@*/ val component = Dagger.create(ExampleComponent::class.java)
 * /*@*/assert(
 * component.numbers == listOf<Number>(1.1f, 2.0, 3, -4L, 5)
 * /*@*/)
 * /*@*/}
 *
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IntoList(
    /**
     * If this is set to `true`, then multi-binding
     * treats return type as a collection, elements of which will all be contributed to the multi-bound list.
     *
     * The return type actually *must* be compatible with a `Collection` interface.
     *
     * See example [here][IntoList], that includes usage of this flag.
     */
    val flatten: Boolean = false,
)