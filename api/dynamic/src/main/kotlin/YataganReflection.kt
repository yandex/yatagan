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

package com.yandex.yatagan.dynamic

import com.yandex.yatagan.dynamic.YataganReflection.complete
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger

/**
 * Clients may (optionally) use this to supply reflection backend with additional parameters, customizing its work.
 * If a client decides to do so, then the setup must be made before any graphs are created.
 *
 * Supply necessary parameters in builder-like fashion, then call [complete] in order to apply the parameters.
 */
public object YataganReflection {
    private var params = ReflectionLoader.Params()

    /**
     * Sets [DynamicValidationDelegate] to be used with all following Yatagan graph creations.
     *
     * `null` by default - no explicit validation is performed.
     */
    @JvmStatic
    public fun validation(delegate: DynamicValidationDelegate?): YataganReflection = apply {
        params.validationDelegate = delegate
    }

    /**
     * Sets max issue encounter path count. No more than [count] paths will be reported for the single message.
     *
     * `5` by default.
     */
    @JvmStatic
    public fun maxIssueEncounterPaths(count: Int): YataganReflection = apply {
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
    @JvmStatic
    public fun strictMode(enabled: Boolean): YataganReflection = apply {
        params.isStrictMode = enabled
    }

    /**
     * Sets logger instance to use.
     *
     * `null` by default.
     */
    @JvmStatic
    public fun logger(logger: Logger?): YataganReflection = apply  {
        params.logger = logger
    }

    /**
     * Invoke this to apply configured parameters to the global Yatagan state.
     * This call can only be made once per `Yatagan` session and before any graphs are created.
     * If not used, the defaults will be automatically applied for every parameter upon first graph creation.
     */
    @JvmStatic
    public fun complete() {
        ReflectionLoader.complete(params)
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
    public fun reset() {
        ReflectionLoader.reset()
        params = ReflectionLoader.Params()
    }
}