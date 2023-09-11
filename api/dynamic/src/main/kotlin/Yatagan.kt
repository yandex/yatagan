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
import com.yandex.yatagan.base.singleInitWithFallback
import com.yandex.yatagan.common.loadAutoBuilderImplementationByComponentClass
import com.yandex.yatagan.common.loadImplementationByBuilderClass
import com.yandex.yatagan.internal.ThreadAssertions
import com.yandex.yatagan.rt.engine.RuntimeEngine
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger

/**
 * Yatagan entry-point object. Create instances of Yatagan components using reflection.
 *
 * Use either [builder] or [create].
 */
public object Yatagan {
    private val engineHolder = singleInitWithFallback { RuntimeEngine(Params()) }
    private var engine: RuntimeEngine<Params> by engineHolder

    /**
     * Configures global Yatagan reflection parameters.
     */
    public class Initializer {
        private val params = Params()

        /**
         * Sets [DynamicValidationDelegate] to be used with all following Yatagan graph creations.
         *
         * `null` by default - no explicit validation is performed.
         */
        public fun validation(delegate: DynamicValidationDelegate?): Initializer = apply {
            params.validationDelegate = delegate
        }

        /**
         * Sets max issue encounter path count. No more than [count] paths will be reported for the single message.
         *
         * `5` by default.
         */
        public fun maxIssueEncounterPaths(count: Int): Initializer = apply {
            require(count >= 1) {
                "Max issue encounter paths can't be less then 1"
            }
            params.maxIssueEncounterPaths = count
        }

        /**
         * Whether all *mandatory warnings* should be promoted to errors.
         *
         * `true` by default.
         */
        public fun strictMode(enabled: Boolean): Initializer = apply {
            params.isStrictMode = enabled
        }

        /**
         * Whether to report duplicate `@Binds` as errors instead of warnings.
         *
         * `false` by default.
         *
         * @see <a href="https://github.com/yandex/yatagan/issues/48">issue #48</a>
         */
        @Deprecated(
            "Will be unconditionally enabled, and the switch will be removed in the next major release",
            level = DeprecationLevel.WARNING,
        )
        public fun reportDuplicateAliasesAsErrors(enabled: Boolean): Initializer = apply {
            params.reportDuplicateAliasesAsErrors = enabled
        }

        /**
         * If `true`, then Yatagan will try to locate and load compiled implementation in the classpath first.
         * Then, if no required classes found, Yatagan will proceed to utilizing reflection backend.
         * If `false` then Yatagan will just use the reflection backend.
         *
         * NOTE: Keep in mind, that build systems may leave stale generated classes in place
         * if incremental compilation/annotation processing is utilized. This may cause stale/invalid implementations
         * being loaded at runtime if Yatagan backend is switched from compiled to dynamic.
         * So if reflection usage is intended, leave this option off.
         *
         * `false` by default.
         */
        public fun useCompiledImplementationIfAvailable(enabled: Boolean): Initializer = apply {
            params.useCompiledImplementationIfAvailable = enabled
        }

        /**
         * If `true`, all conditions in a component are lazily evaluated.
         * If `false` (default), some conditions may be evaluated in the component's constructor - this mode is soon
         *  to be deprecated.
         */
        public fun allConditionsLazy(enabled: Boolean): Initializer = apply {
            params.allConditionsLazy = enabled
        }

        /**
         * Sets logger instance to use.
         *
         * `null` by default.
         */
        public fun logger(logger: Logger?): Initializer = apply {
            params.logger = logger
        }

        /**
         * Invoke this to apply configured parameters to the global Yatagan state.
         * This call can only be made once per `Yatagan` session and before any graphs are created.
         * If not used, the defaults will be automatically applied for every parameter upon first graph creation.
         */
        public fun apply() {
            engine = RuntimeEngine(params.copy())
        }
    }

    /**
     * Sets [ThreadAsserter] object to be used in Single Thread component implementations.
     */
    @JvmStatic
    public fun setThreadAsserter(threadAsserter: ThreadAsserter?) {
        ThreadAssertions.setAsserter(threadAsserter)
    }

    /**
     * Clients may (optionally) call this to supply reflection backend with additional parameters, customizing its work.
     * If a client decides to use it, then the call must be made before any graphs are created.
     */
    @JvmStatic
    public fun setupReflectionBackend(): Initializer {
        return Initializer()
    }

    /**
     * Clients may optionally call this method after all the work with Yatagan graphs is done.
     * This call clears all internal global states and caches, freeing memory.
     * **Any further usage of already created graphs may result in Undefined Behavior**,
     * so make sure no methods on any created components are called after `shutdown()`.
     *
     * NOTE: In most of the cases **it's best not to call this method at all**, as DI is often being used up until
     * the very application termination, and it doesn't make much sense to call `reset()` right before that.
     */
    @JvmStatic
    public fun resetReflectionBackend() {
        if (engineHolder.isInitialized()) {
            engine.destroy()
            engineHolder.deinitialize()
        }
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
        if (engine.params.useCompiledImplementationIfAvailable) {
            try {
                return loadImplementationByBuilderClass(builderClass).also {
                    engine.params.logger?.log("Found generated implementation for `$builderClass`, using it")
                }
            } catch (_: ClassNotFoundException) {
                // Fallback to full reflection
            }
        }

        return engine.builder(builderClass)
    }

    /**
     * Use this to create an "auto"-builder for components, that do not declare an explicit [Component.Builder].
     *
     * @see AutoBuilder
     */
    @JvmStatic
    public fun <T : Any> autoBuilder(componentClass: Class<T>): AutoBuilder<T> {
        if (engine.params.useCompiledImplementationIfAvailable) {
            try {
                return loadAutoBuilderImplementationByComponentClass(componentClass).also {
                    engine.params.logger?.log("Found generated implementation for `$componentClass`, using it")
                }
            } catch (_: ClassNotFoundException) {
                // Fallback to full reflection
            }
        }
        return engine.autoBuilder(componentClass)
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
    public fun <T : Any> create(componentClass: Class<T>): T {
        return autoBuilder(componentClass).create()
    }

    private data class Params (
        override var validationDelegate: DynamicValidationDelegate? = null,
        override var maxIssueEncounterPaths: Int = 5,
        override var isStrictMode: Boolean = true,
        override var logger: Logger? = null,
        override var reportDuplicateAliasesAsErrors: Boolean = false,
        override var allConditionsLazy: Boolean = false,
        var useCompiledImplementationIfAvailable: Boolean = false,
    ) : RuntimeEngine.Params
}