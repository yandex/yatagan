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
 * A **binding** declaration marker.
 *
 * Annotates *abstract* methods of a [Module] that *delegate* bindings.
 * In general, method parameters' (if any, see below) types must be assignable to the return type.
 *
 * `@Binds` usage variants by example:
 *
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.yatagan.*
 * /*@*/ import javax.inject.*
 *
 * /*@*/ interface Api
 * /*@*/ class Impl1 @Inject constructor() : Api {}
 * /*@*/ class Impl2 @Inject constructor() : Api {}
 * /*@*/ class Stub @Inject constructor() : Api {}
 *
 * /*@*/ @Module interface TestModule1 {
 * // `@Binds` methods with a single parameter constitutes an **alias** binding:
 * // whenever `Api` is requested, `Impl` is injected.
 * // This is the basic and the most common usage:
 * @Binds
 * fun aliasForApi(i: Impl1): Api
 * /*@*/ }
 *
 * // Other variants are more advanced cases, that are part of the *Conditions API*.
 * //
 *
 * // `@Binds` methods with *more than one* parameter constitute an **alternatives** binding:
 * /*@*/ @Module interface TestModule2 {
 * @Binds
 * fun alternatives(impl: Impl1, orThis: Impl2, fallback: Stub): Api
 * /*@*/}
 *
 * // `@Binds` methods with *zero* parameters constitute an explicit **empty** or **absent** binding.
 * // Directly requesting `Api` will always be a condition violation,
 * // while `Optional<Api>` dependencies will always be *empty*.
 * /*@*/ @Module interface TestModule3 {
 * @Binds
 * fun noApi(): Api
 * /*@*/}
 *
 * /*@*/ @Component(modules=[TestModule1::class]) interface TestComponent1 { val api: Api }
 * /*@*/ @Component(modules=[TestModule2::class]) interface TestComponent2 { val api: Api }
 * /*@*/ @Component(modules=[TestModule3::class]) interface TestComponent3 { val api: Optional<Api> }
 * ```
 * All these cases can be [qualified][javax.inject.Qualifier].
 * The **alternatives** can also be [scoped][javax.inject.Scope].
 * All these cases can be [multi-bindings][IntoList].
 *
 * @see Provides
 * @see Optional
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class Binds(
)