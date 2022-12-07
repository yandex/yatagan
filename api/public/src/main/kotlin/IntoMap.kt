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
 * A special modifier annotation that can be applied together with [Binds] or [Provides].
 *
 * When applied, a *binding* becomes a *multi-binding* - a `java.util.Map<K, V>`, where:
 * 1. `K` is the type of the `value` attribute of the annotation on this binding,
 *  which itself is marked by @[IntoMap.Key] (further in docs - a *key-annotation*).
 * 2. `V` is the target type of this binding (the return type).
 *
 * Such binding contributes a pair of `(K, V)`, where K is the compile-time value of *key-annotation's* value attribute,
 * and value is the target if this binding, to a map, which becomes injectable in the graph. There can be multiple
 * such pairs that are contributed to the same map.
 *
 * A map is defined by the pair of exact types `K` and `V`, and can be qualified as the usual binding.
 * A *qualifier* annotation on a `IntoMap`-marked binding goes to the map type, not to the immediate target, which is
 * to be contributed to the map.
 *
 * There must only be a single *key-annotation* on every `@IntoMap` binding.
 * A *key-annotation's* value, and, thus, a map's key can have any type a normal annotation attribute can have,
 * except for **arrays** and nested **annotations**.
 *
 * For each key value there can only be one value.
 *
 * There are a few "builtin" key-annotations, that are considered as most commonly used:
 * - [ClassKey]
 * - [IntKey]
 * - [StringKey]
 *
 * Though, other key-annotations can be easily declared.
 *
 * Example:
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.yatagan.*
 * /*@*/ import javax.inject.*
 *
 * /*@*/ class A; class B; class C
 *
 * /*@*/@Module interface TestModule {
 * /*@*/ companion object {
 *
 * @IntoMap @ClassKey(A::class) @Provides
 * fun provideValueForA(): Number = 1
 *
 * @IntoMap @ClassKey(B::class) @Provides
 * fun provideValueForB(): Number = 2L
 **
 * // A `@Binds` can be also used with `@IntoMap`:
 *
 * @Provides @Named("float") fun floatValue(): Float = 3.0f
 *
 * /*@*/}
 *
 * @IntoMap @ClassKey(C::class) @Binds
 * fun bindValueForC(@Named("float") f: Float): Number
 *
 * /*@*/}
 *
 * // Then the following entry-point may be declared inside the component:
 * /*@*/ @Component(modules = [TestModule::class]) interface ExampleComponent {
 * val mapOfClassToNumber: Map<Class<*>, Number>
 * /*@*/}
 *
 * // And the following would hold true:
 * /*@*/ fun test() {
 * /*@*/ val component = Yatagan.create(ExampleComponent::class.java)
 * /*@*/assert(
 * component.mapOfClassToNumber == mapOf(A::class to 1, B::class to 2, C::class to 3.0f)
 * /*@*/)
 * /*@*/}
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class IntoMap {

    /**
     * Denotes a key-annotation for [IntoMap] multi-binding modifier.
     *
     * The target annotation must have a `value` attribute which will be used a map key value.
     *
     * Example for constrained class keys, that should only accept types implementing `MyApi`:
     * ```kotlin
     * /*@*/ import kotlin.reflect.KClass
     * /*@*/ import com.yandex.yatagan.*
     * /*@*/ interface MyApi
     * @IntoMap.Key
     * @Retention(AnnotationRetention.RUNTIME)
     * @Target(AnnotationTarget.FUNCTION)
     * annotation class MyApiClassKey(val value: KClass<out MyApi>)
     * ```
     * That would form a map with the key type `java.lang.Class<? extends MyApi>`.
     */
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.ANNOTATION_CLASS)
    public annotation class Key
}