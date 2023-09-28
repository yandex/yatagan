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
 * If [isRoot] is set to `true` (default), then it acts like a normal Dagger component,
 * see Dagger [docs](https://dagger.dev/api/latest/dagger/Component.html).
 *
 * If [isRoot] is set to `false`, then it acts like a Dagger
 * [Subcomponent](https://dagger.dev/api/latest/dagger/Subcomponent.html)
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class Component(
    /**
     * If `true` (default), then the component is a root of a hierarchy and may be created directly.
     * Otherwise, the component serves as a subcomponent and must be installed into its parent via
     * [Module.subcomponents] attribute.
     */
    val isRoot: Boolean = true,

    /**
     * A list of modules to install into the component, recursively using [Module.includes].
     */
    val modules: Array<KClass<*>> = [],

    /**
     * A list of *component dependencies* for the component.
     *
     * @see Component
     */
    val dependencies: Array<KClass<*>> = [],

    /**
     * A list of [flavors][ComponentFlavor].
     * No more than one flavor per dimension may be present in a variant.
     * If this component is a child, then the effective variant will be extended by the parent's effective variant.
     * Thus, a child can't declare flavors for dimensions, that are already declared in parents.
     *
     * The main idea behind the *variant system* is to be able to have the following hierarchies (example):
     * ```kotlin
     * @ComponentVariantDimension annotation class Device {
     *   @ComponentFlavor(dimension = Device::class) annotation class Tablet
     *   @ComponentFlavor(dimension = Device::class) annotation class Phone
     *   @ComponentFlavor(dimension = Device::class) annotation class Watch
     * }
     *
     * @Component
     * interface MyApplicationComponent
     *
     * interface MyUiComponent { /* some entry-point */ }
     *
     * @Component(isRoot = false, variant = [Device.Phone::class])
     * interface MyPhoneUiComponent : MyUiComponent
     *
     * @Component(isRoot = false, variant = [Device.Watch::class])
     * interface MyWatchUiComponent : MyUiComponent
     * ```
     *
     * @see Conditional
     */
    val variant: Array<KClass<out Annotation>> = [],

    /**
     * If `true`, then the component's implementation is guaranteed to be thread-safe.
     * If `false`, then the implementation is not thread-safe;
     * Furthermore, every [Lazy]/[javax.inject.Provider] issued by the component is not thread-safe.
     *
     * thread-unsafe implementations *may* have increased performance in a single-thread environment.
     * For single-thread implementations, the thread-access is checked via [ThreadAssertions].
     */
    val multiThreadAccess: Boolean = false,
) {
    /**
     * Can be used both as Dagger's [Builder](https://dagger.dev/api/latest/dagger/Component.Builder.html)
     * and [Factory](https://dagger.dev/api/latest/dagger/Component.Factory.html).
     *
     * Component creator interface is mandatory for non-root components.
     * No automatic builder/factory deduction is done.
     *
     * Example:
     * ```kotlin
     * @Component
     * interface TestComponent {
     *   @Component.Builder
     *   interface Creator {
     *     @BindsInstance @Named("ratio") fun setRatio(ratio: Double): Creator
     *     @BindsInstance @Named("count") fun setCount(count: Int)
     *     fun create(
     *       @BindsInstance @Named("id") id: Int,
     *       @BindsInstance @Named("string") name: String,
     *     ): TestComponent
     *   }
     * }
     * ```
     */
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    public annotation class Builder
}