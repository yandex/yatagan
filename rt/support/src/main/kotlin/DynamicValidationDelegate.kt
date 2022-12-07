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

package com.yandex.yatagan.rt.support

import com.yandex.yatagan.validation.RichString

/**
 * A delegate interface, that provides API for clients, that wish to enable full Yatagan validation
 * routine for reflection backend.
 *
 * Such API is required, as straightforward approach with full validation performed at the moment of graph
 * construction may be overly costly for large graphs. Such approach penalizes the __good path__ if no errors are
 * present in a graph.
 *
 * @see SimpleDynamicValidationDelegate
 * @see AsyncDynamicValidationDelegate
 */
interface DynamicValidationDelegate {
    /**
     * Whether to load and use [com.yandex.yatagan.validation.spi.ValidationPluginProvider]s.
     */
    val usePlugins: Boolean

    /**
     * A promise-like handle that is used to await validation completion.
     */
    interface Promise {
        /**
         * Must block until the underlying validation is complete.
         * After this method returns, an error is likely to be thrown, so implementations are advised to ensure
         *  all reporting and flushing work is complete before returning.
         *
         * @see dispatchValidation
         */
        fun await()
    }

    /**
     * A validation reporting API.
     *
     * @see dispatchValidation
     */
    interface ReportingDelegate {
        /**
         * Invoked by the framework to report an error.
         * If this gets invoked at least once for graph, then the graph is invalid.
         */
        fun reportError(message: RichString)

        /**
         * Invoked by the framework to report a warning.
         */
        fun reportWarning(message: RichString)
    }

    /**
     * A functional interface for a validation operation.
     */
    fun interface Operation {
        /**
         * Runs validation.
         *
         * @param reporting a delegate for the framework to report messages back to the caller.
         */
        fun validate(reporting: ReportingDelegate)
    }

    /**
     * Enqueue/execute validation operation for a component hierarchy.
     * The implementation may choose to invoke [operation]:
     *  - Synchronously. Then `null` may be returned. If validation yielded any errors, then the implementation may
     *   throw an exception to prevent the framework from building an invalid graph.
     *  - Asynchronously. Then the promise object must be returned; The implementation may delay the execution of the
     *   [operation] and/or execute it on any thread it seems fit. When [Promise.await] is called, the implementation
     *   must "join" validation procedure.
     *
     * @param title a framework-provided string which describes which graph is being validated.
     *  Can be used for internal reporting.
     *
     * @param operation a validation [procedure][Operation].
     *
     * @return an optional "validation promise" object. Synchronous implementations can return `null` here.
     *  A returned promise object may be used by the framework to [await][Promise.await] on implementation
     *  to complete the validation.
     *  This is done when a _critical error occurs in a framework because the graph is most likely invalid_.
     *  Only after validation is "joined", the framework throws an exception.
     *  Such mechanism allows the implementation to defer validation until it's clear that the graph is actually invalid
     *  and details on that are required.
     */
    fun dispatchValidation(
        title: String,
        operation: Operation,
    ): Promise?
}