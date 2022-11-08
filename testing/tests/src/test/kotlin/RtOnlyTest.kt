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

            class `Dagger${'$'}MyComponent` : MyComponent {
                override fun getInt(): Int = ID1
                companion object {
                    @JvmStatic fun create() = `Dagger${'$'}MyComponent`()
                }
            }

            class `Dagger${'$'}MyComponentWithBuilder` : MyComponentWithBuilder {
                override fun getInt(): Int = ID2
                companion object {
                    @JvmStatic fun builder() = object : MyComponentWithBuilder.Builder {
                        override fun create() = `Dagger${'$'}MyComponentWithBuilder`()
                    }
                }
            }

            fun test() {
                val c1: MyComponent = Dagger.create(MyComponent::class.java)
                val c2: MyComponentWithBuilder = Dagger.builder(MyComponentWithBuilder.Builder::class.java).create()
                assert(c1.getInt() == ID1)
                assert(c2.getInt() == ID2)
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}