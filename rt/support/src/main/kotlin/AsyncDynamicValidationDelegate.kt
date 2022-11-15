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
class AsyncDynamicValidationDelegate @JvmOverloads constructor(
    private val executorService: ExecutorService,
    private val base: DynamicValidationDelegate = SimpleDynamicValidationDelegate(),
) : DynamicValidationDelegate {

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
