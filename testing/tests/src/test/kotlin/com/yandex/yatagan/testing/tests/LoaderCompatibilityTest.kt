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

import com.yandex.yatagan.generated.ClasspathForCompatCheck
import com.yandex.yatagan.generated.CurrentClasspath
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class LoaderCompatibilityTest(
    private val version: Version,
) {
    @Suppress("EnumEntryName")
    enum class Version(
        val kaptClasspath: String,
        val apiClasspath: String,
    ) {
        v1_0_0(ClasspathForCompatCheck.Kapt1_0_0, ClasspathForCompatCheck.Api1_0_0),
        v1_1_0(ClasspathForCompatCheck.Kapt1_1_0, ClasspathForCompatCheck.Api1_1_0),
        v1_2_0(ClasspathForCompatCheck.Kapt1_2_0, ClasspathForCompatCheck.Api1_2_0),
        v1_3_0(ClasspathForCompatCheck.Kapt1_3_0, ClasspathForCompatCheck.Api1_3_0),
        v1_5_0(ClasspathForCompatCheck.Kapt1_5_0, ClasspathForCompatCheck.Api1_5_0),
        v1_6_0(ClasspathForCompatCheck.Kapt1_6_0, ClasspathForCompatCheck.Api1_6_0),
    }

    companion object {
        @JvmStatic
        @Parameters(name = "with {0}")
        fun parameters() = Version.entries
    }

    @Test
    fun `'create' is compatible with code generated with `() = with(JapCompileTestDriver(
        customProcessorClasspath = version.kaptClasspath,
        apiClasspath = version.apiClasspath,
        runtimeApiClasspath = CurrentClasspath.ApiCompiled,
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
        customProcessorClasspath = version.kaptClasspath,
        apiClasspath = version.apiClasspath,
        runtimeApiClasspath = CurrentClasspath.ApiCompiled,
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
        customProcessorClasspath = version.kaptClasspath,
        apiClasspath = version.apiClasspath,
        runtimeApiClasspath = CurrentClasspath.ApiCompiled,
        checkGoldenOutput = false,
    )) {
        Assume.assumeTrue(version >= Version.v1_2_0)  // Auto-builder was added in 1.2.0

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