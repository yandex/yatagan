/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.processor.common.StringOption
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ReusableLazyTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `lazy retains its result after reusable cache overwrite`() = checkCacheOverwrite(optional = false)

    @Test
    fun `optional lazy retains its result after reusable cache overwrite`() = checkCacheOverwrite(optional = true)

    private fun checkCacheOverwrite(optional: Boolean) {
        forbidThreadAssertions()
        val lazyType = if (optional) "Optional<Lazy<Value>>" else "Lazy<Value>"
        val lazyExpression = if (optional) "component.lazy().get()" else "component.lazy()"
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.Executors
            import java.util.concurrent.TimeUnit.SECONDS
            import java.util.concurrent.atomic.AtomicInteger
            import javax.inject.Provider

            class Value

            @Module
            object Bindings {
                val started = CountDownLatch(1)
                val resume = CountDownLatch(1)
                val calls = AtomicInteger()

                @Provides @Reusable
                fun value(): Value {
                    if (calls.incrementAndGet() == 1) {
                        started.countDown()
                        check(resume.await(10, SECONDS))
                    }
                    return Value()
                }
            }

            @Component(multiThreadAccess = true, modules = [Bindings::class])
            interface TestComponent {
                fun provider(): Provider<Value>
                fun lazy(): $lazyType
            }

            fun test() {
                val component = Yatagan.create(TestComponent::class.java)
                val provider = component.provider()
                val lazy = $lazyExpression
                check(Bindings.calls.get() == 0)
                val executor = Executors.newFixedThreadPool(2)
                val start = CountDownLatch(1)
                try {
                    val pending = executor.submit<Value> { provider.get() }
                    check(Bindings.started.await(10, SECONDS))
                    val calling = CountDownLatch(1)
                    val concurrent = executor.submit<Value> {
                        calling.countDown()
                        check(start.await(10, SECONDS))
                        lazy.get()
                    }
                    check(calling.await(10, SECONDS))
                    // Release both callers before either has initialized this Lazy.
                    // The test accepts either order of their calls and any locking strategy.
                    start.countDown()
                    val first = lazy.get() // Both Lazy calls must return A while the Provider is paused.
                    check(concurrent.get(10, SECONDS) === first) {
                        "Concurrent callers of one Lazy returned different Reusable instances"
                    }
                    Bindings.resume.countDown()
                    val later = pending.get(10, SECONDS) // Publishes B over A.
                    check(first !== later)
                    check(lazy.get() === first) { "One Lazy returned a different Reusable instance" }
                    check(executor.submit<Value> { lazy.get() }.get(10, SECONDS) === first) {
                        "One Lazy returned a different Reusable instance to another thread"
                    }
                    check(provider.get() === later) // Provider still follows the component cache.
                    val laterLazy = $lazyExpression
                    check(laterLazy.get() === later)
                    check(Bindings.calls.get() == 2)
                } finally {
                    start.countDown()
                    Bindings.resume.countDown()
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, SECONDS))
                }
            }
        """.trimIndent())
        compileRunAndValidate()
    }

    @Test
    fun `multi-threaded reusable lazy retries after failure`() = checkRetry(multiThreadAccess = true)

    @Test
    fun `single-threaded reusable lazy retries after failure`() = checkRetry(multiThreadAccess = false)

    private fun checkRetry(multiThreadAccess: Boolean) {
        if (multiThreadAccess) forbidThreadAssertions()
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.Inject

            object Calls { var count = 0 }

            @Reusable
            class Value @Inject constructor() {
                init {
                    if (++Calls.count == 1) error("First creation fails")
                }
            }

            @Component(multiThreadAccess = $multiThreadAccess)
            interface TestComponent {
                fun lazy(): Lazy<Value>
            }

            fun test() {
                val component = Yatagan.create(TestComponent::class.java)
                val lazy = component.lazy()
                check(Calls.count == 0)
                val failure = runCatching { lazy.get() }.exceptionOrNull()
                check(generateSequence(failure) { it.cause }.any {
                    it is IllegalStateException && it.message == "First creation fails"
                })
                val value = lazy.get()
                check(lazy.get() === value)
                check(component.lazy().get() === value)
                check(Calls.count == 2)
            }
        """.trimIndent())
        compileRunAndValidate()
    }

    private fun forbidThreadAssertions() {
        givenOption(StringOption.ThreadCheckerClassName, "test.UnexpectedThreadChecker")
        givenKotlinSource("test.UnexpectedThreadChecker", """
            object UnexpectedThreadChecker {
                @JvmStatic fun assertThreadAccess() {
                    error("Thread checker called for a multi-threaded component")
                }
            }
        """.trimIndent())
    }
}
