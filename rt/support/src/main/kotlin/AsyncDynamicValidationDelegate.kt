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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * An implementation for a [DynamicValidationDelegate], that schedules operations asynchronously.
 * This implementation is composable, accepting an underlying delegate.
 *
 * @param executorService an executor service to use for dispatching validation operations.
 * @param base underlying implementation to use.
 *   Asynchronous scheduling will call its [dispatchValidation][DynamicValidationDelegate.dispatchValidation].
 *   If that is also asynchronous, its promise will be awaited, blocking the executor thread.
 */
open class AsyncDynamicValidationDelegate @JvmOverloads constructor(
    private val executorService: ExecutorService,
    private val base: DynamicValidationDelegate = SimpleDynamicValidationDelegate(),
) : DynamicValidationDelegate {

    override val logger: Logger?
        get() = base.logger

    override val usePlugins: Boolean
        get() = base.usePlugins

    override fun dispatchValidation(
        title: String,
        operation: DynamicValidationDelegate.Operation,
    ): DynamicValidationDelegate.Promise {
        val future: Future<*> = executorService.submit {
            val nestedPromise = base.dispatchValidation(title, operation)
            nestedPromise?.await()
        }
        return FuturePromise(future)
    }

    private class FuturePromise(
        val future: Future<*>,
    ) : DynamicValidationDelegate.Promise {
        override fun await() {
            try {
                future.get()
            } catch (_: InterruptedException) {
                // Swallow
            }
        }
    }
}
