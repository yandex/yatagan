package com.yandex.yatagan

import com.yandex.yatagan.base.singleInitWithFallback
import com.yandex.yatagan.common.loadImplementationByBuilderClass
import com.yandex.yatagan.common.loadImplementationByComponentClass
import com.yandex.yatagan.rt.engine.RuntimeEngine
import com.yandex.yatagan.internal.ThreadAssertions
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger

/**
 * Yatagan entry-point object. Create instances of Yatagan components using reflection.
 *
 * Use either [builder] or [create].
 */
object Yatagan {
    private val engineHolder = singleInitWithFallback { RuntimeEngine(Params()) }
    private var engine: RuntimeEngine<Params> by engineHolder

    /**
     * Configures global Yatagan reflection parameters.
     */
    class Initializer {
        private val params = Params()

        /**
         * Sets [DynamicValidationDelegate] to be used with all following Yatagan graph creations.
         *
         * `null` by default - no explicit validation is performed.
         */
        fun validation(delegate: DynamicValidationDelegate?): Initializer = apply {
            params.validationDelegate = delegate
        }

        /**
         * Sets max issue encounter path count. No more than [count] paths will be reported for the single message.
         *
         * `5` by default.
         */
        fun maxIssueEncounterPaths(count: Int): Initializer = apply {
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
        fun strictMode(enabled: Boolean): Initializer = apply {
            params.isStrictMode = enabled
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
        fun useCompiledImplementationIfAvailable(enabled: Boolean): Initializer = apply {
            params.useCompiledImplementationIfAvailable = enabled
        }

        /**
         * Sets logger instance to use.
         *
         * `null` by default.
         */
        fun logger(logger: Logger?): Initializer = apply {
            params.logger = logger
        }

        /**
         * Invoke this to apply configured parameters to the global Yatagan state.
         */
        fun apply() {
            engine = RuntimeEngine(params.copy())
        }
    }

    /**
     * Sets [ThreadAsserter] object to be used in Single Thread component implementations.
     */
    @JvmStatic
    fun setThreadAsserter(threadAsserter: ThreadAsserter?) {
        ThreadAssertions.setAsserter(threadAsserter)
    }

    /**
     * Clients may (optionally) call this to supply reflection backend with additional parameters, customizing its work.
     * If a client decides to use it, then the call must be made before any graphs are created.
     */
    @JvmStatic
    fun setupReflectionBackend(): Initializer {
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
    fun resetReflectionBackend() {
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
    fun <T : Any> builder(builderClass: Class<T>): T {
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
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    fun <T : Any> create(componentClass: Class<T>): T {
        if (engine.params.useCompiledImplementationIfAvailable) {
            try {
                return loadImplementationByComponentClass(componentClass).also {
                    engine.params.logger?.log("Found generated implementation for `$componentClass`, using it")
                }
            } catch (_: ClassNotFoundException) {
                // Fallback to full reflection
            }
        }
        return engine.create(componentClass)
    }

    private data class Params (
        override var validationDelegate: DynamicValidationDelegate? = null,
        override var maxIssueEncounterPaths: Int = 5,
        override var isStrictMode: Boolean = true,
        override var logger: Logger? = null,
        var useCompiledImplementationIfAvailable: Boolean = false,
    ) : RuntimeEngine.Params
}