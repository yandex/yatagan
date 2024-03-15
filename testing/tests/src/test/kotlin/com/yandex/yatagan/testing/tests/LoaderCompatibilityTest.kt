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

package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.generated.KaptClasspathForCompatCheck1_0_0
import com.yandex.yatagan.generated.KaptClasspathForCompatCheck1_1_0
import com.yandex.yatagan.generated.KaptClasspathForCompatCheck1_2_0
import com.yandex.yatagan.generated.KaptClasspathForCompatCheck1_3_0
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class LoaderCompatibilityTest(
    @Suppress("unused")
    private val version: String,
    private val kaptClasspath: String,
) {
    companion object {
        @JvmStatic
        @Parameters(name = "with {0}")
        fun parameters() = listOf(
            arrayOf("1.0.0", KaptClasspathForCompatCheck1_0_0),
            arrayOf("1.1.0", KaptClasspathForCompatCheck1_1_0),
            arrayOf("1.2.0", KaptClasspathForCompatCheck1_2_0),
            arrayOf("1.3.0", KaptClasspathForCompatCheck1_3_0),
        )
    }

    @Test
    fun `'create' is compatible with code generated with `() = with(JapCompileTestDriver(
        processorClasspath = kaptClasspath,
        checkGoldenOutput = false,
    )) {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            @Module 
            class MyModule {
                @Provides fun provideInt() = 3
            }
            @Component(modules = [MyModule::class])
            interface MyComponent {
                fun getInt(): Int
            }

            fun test() {
                val c = Yatagan.create(MyComponent::class.java)
                assert(c.getInt() == 3)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `'builder' is compatible with code generated `() = with(JapCompileTestDriver(
        processorClasspath = kaptClasspath,
        checkGoldenOutput = false,
    )) {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            @Module 
            class MyModule {
                @Provides fun provideInt() = 3
            }
            @Component(modules = [MyModule::class])
            interface MyComponent {
                fun getInt(): Int
                
                @Component.Builder interface Creator {
                    fun create(module: MyModule): MyComponent
                }
            }

            fun test() {
                val c = Yatagan.builder(MyComponent.Creator::class.java).create(MyModule())
                assert(c.getInt() == 3)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `'autoBuilder' is compatible with code generated `() = with(JapCompileTestDriver(
        processorClasspath = kaptClasspath,
        checkGoldenOutput = false,
    )) {
        Assume.assumeTrue(version >= "1.2.0")  // Auto-builder was added in 1.2.0

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            @Module 
            class MyModule {
                @Provides fun provideInt() = 3
            }
            @Component(modules = [MyModule::class])
            interface MyComponent {
                fun getInt(): Int
            }

            fun test() {
                val c = Yatagan.autoBuilder(MyComponent::class.java)
                    .provideInput(MyModule())
                    .create()
                assert(c.getInt() == 3)
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}