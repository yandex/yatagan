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
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.Inject
            import javax.inject.Provider

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
            }

            fun test() {
                val component = Yatagan.create(TestComponent::class.java)
                val constructed = component.constructorValue()
                val provided = component.providedValue()
                check(constructed.payload == 42)
                check(provided.payload == 42)
                check(component.constructorProvider().get() === constructed)
                check(component.providedProvider().get() === provided)
            }
        """.trimIndent())
        compileRunAndValidate()
    }
}
