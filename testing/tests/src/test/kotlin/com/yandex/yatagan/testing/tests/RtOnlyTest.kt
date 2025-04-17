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

class RtOnlyTest : CompileTestDriver by DynamicCompileTestDriver() {
    @Test
    fun `check generated implementation is loaded when available`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*

            @Component interface MyComponent {
                fun getInt(): Int
            }

            @Component interface MyComponentWithBuilder {
                fun getInt(): Int
                
                @Component.Builder interface Builder {
                    fun create(): MyComponentWithBuilder
                }
            }

            const val ID1 = 1234567
            const val ID2 = 89101112

            class YataganMyComponent : MyComponent {
                override fun getInt(): Int = ID1
                companion object {
                    @JvmStatic fun autoBuilder() = object : AutoBuilder<MyComponent> {
                        override fun <I : Any> provideInput(i: I, c: Class<I>) = throw AssertionError()
                        override fun create() = YataganMyComponent()
                    }
                }
            }

            class YataganMyComponentWithBuilder : MyComponentWithBuilder {
                override fun getInt(): Int = ID2
                companion object {
                    @JvmStatic fun builder() = object : MyComponentWithBuilder.Builder {
                        override fun create() = YataganMyComponentWithBuilder()
                    }
                }
            }

            fun test() {
                val c1: MyComponent = Yatagan.create(MyComponent::class.java)
                val c12: MyComponent = Yatagan.autoBuilder(MyComponent::class.java).create()
                val c2: MyComponentWithBuilder = Yatagan.builder(MyComponentWithBuilder.Builder::class.java).create()
                assert(c1.getInt() == ID1)
                assert(c12.getInt() == ID1)
                assert(c2.getInt() == ID2)
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}