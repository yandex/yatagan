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

package com.yandex.yatagan.testing.tests

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class KcpCompileTestDriverTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers(
            includeKsp = false,
            includeJap = false,
            includeRt = false,
            includeKcp = true,
        )
    }

    @Test
    fun `component with inject constructor runs through the shared harness`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject

            class Dependency @Inject constructor()

            @Component
            interface TestComponent {
                fun dependency(): Dependency
            }

            fun test() {
                check(Yatagan.create(TestComponent::class.java).dependency() is Dependency)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `transitive inject constructors run through the shared harness`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject

            class Leaf @Inject constructor()
            class Branch @Inject constructor(val leaf: Leaf)

            @Component
            interface TestComponent {
                fun branch(): Branch
            }

            fun test() {
                check(Yatagan.create(TestComponent::class.java).branch().leaf is Leaf)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `validation-only KCP runs object module through the dynamic backend`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan

            @Module
            object TestModule {
                @Provides
                fun value(): Any = "provided"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun value(): Any
            }

            fun test() {
                check(Yatagan.create(TestComponent::class.java).value() == "provided")
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `java source is skipped`() {
        givenJavaSource("test.JavaType", "public class JavaType {}")

        compileRunAndValidate()
    }

    @Test
    fun `kotlin sources with the same file name are both compiled`() {
        givenKotlinSource("first.Dependency", "class Dependency")
        givenKotlinSource("second.Dependency", "class Dependency")
        givenKotlinSource("test.TestCase", """
            fun test() {
                check(first.Dependency()::class.java.name == "first.Dependency")
                check(second.Dependency()::class.java.name == "second.Dependency")
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}
