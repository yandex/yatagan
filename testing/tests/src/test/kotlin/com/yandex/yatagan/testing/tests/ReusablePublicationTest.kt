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
class ReusablePublicationTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `multi-threaded reusable caches`() = checkCaches(multiThreadAccess = true)

    @Test
    fun `single-threaded reusable caches`() = checkCaches(multiThreadAccess = false)

    private fun checkCaches(multiThreadAccess: Boolean) {
        givenOption(StringOption.ThreadCheckerClassName, "test.ThreadChecks")
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import java.util.concurrent.Executors
            import java.util.concurrent.TimeUnit.SECONDS
            import javax.inject.Inject
            import javax.inject.Provider

            object ThreadChecks {
                lateinit var owner: Thread
                @JvmStatic fun assertThreadAccess() {
                    check(Thread.currentThread() === owner) { "Access on non-owner thread" }
                }
            }

            @Reusable
            class ConstructorValue @Inject constructor() {
                var payload: Int = 42
            }

            class ProvidedValue {
                var payload: Int = 0
            }

            @Module
            object Bindings {
                @Provides @Reusable
                fun value(): ProvidedValue = ProvidedValue().also { it.payload = 42 }
            }

            @Component(multiThreadAccess = $multiThreadAccess, modules = [Bindings::class])
            interface TestComponent {
                fun constructorValue(): ConstructorValue
                fun providedValue(): ProvidedValue
                fun constructorProvider(): Provider<ConstructorValue>
                fun providedProvider(): Provider<ProvidedValue>
                fun constructorLazy(): Lazy<ConstructorValue>
                fun providedLazy(): Lazy<ProvidedValue>
            }

            fun checkValues(component: TestComponent) {
                val constructed = component.constructorValue()
                val provided = component.providedValue()
                check(constructed.payload == 42)
                check(provided.payload == 42)
                check(component.constructorProvider().get() === constructed)
                check(component.providedProvider().get() === provided)
                check(component.constructorLazy().get() === constructed)
                check(component.providedLazy().get() === provided)
            }

            fun test() {
                ThreadChecks.owner = Thread.currentThread()
                val component = Yatagan.create(TestComponent::class.java)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    if ($multiThreadAccess) {
                        executor.submit { checkValues(component) }.get(10, SECONDS)
                    } else {
                        executor.submit {
                            val accesses = listOf<() -> Any>(
                                { component.constructorValue() },
                                { component.providedValue() },
                                { component.constructorProvider().get() },
                                { component.providedProvider().get() },
                                { component.constructorLazy().get() },
                                { component.providedLazy().get() },
                            )
                            for (access in accesses) {
                                val failure = runCatching { access() }.exceptionOrNull()
                                check(generateSequence(failure) { it.cause }.any {
                                    it is IllegalStateException && it.message == "Access on non-owner thread"
                                }) { "Single-threaded component accepted access from another thread" }
                            }
                        }.get(10, SECONDS)
                        checkValues(component)
                    }
                } finally {
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, SECONDS))
                }
            }
        """.trimIndent())
        compileRunAndValidate()
    }
}
