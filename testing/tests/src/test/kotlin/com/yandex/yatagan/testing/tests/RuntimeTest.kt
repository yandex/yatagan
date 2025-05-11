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

import com.yandex.yatagan.processor.common.StringOption
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class RuntimeTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `check qualified providings are correct`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Named
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Yatagan

            class Simple(val value: String)

            @Module
            object MyModule {
                @Provides fun simple() = Simple("simple")
                @Provides @Named("one") fun one() = Simple("one")
                @Provides @Named("two") fun two() = Simple("two")                
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun simple(): Simple
                @Named("one") fun one(): Simple
                @Named("two") fun two(): Simple
            }

            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c.simple().value == "simple")
                assert(c.one().value == "one")
                assert(c.two().value == "two")
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - included modules are deduplicated`() {
        givenJavaSource("test.Simple", """
            public class Simple {
                String value;
                
                public Simple(String v) {
                    value = v;
                }
                
                public String getValue() {
                    return value;
                }
            }
        """)

        givenJavaSource("test.MyModuleHello", """
            import javax.inject.Named;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;

            @Module(includes = {MyModuleBye.class, MyModuleFoo.class})
            public interface MyModuleHello {
                @Provides @Named("hello") static Simple hello() { return new Simple("hello"); } 
            }
        """)

        givenJavaSource("test.MyModuleBye", """
            import javax.inject.Named;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
            @Module(includes = {MyModuleFoo.class})
            public interface MyModuleBye{
                @Provides @Named("bye") static Simple bye() { return new Simple("bye"); } 
            }
        """)

        givenJavaSource("test.MyModuleFoo", """
            import javax.inject.Named;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
            @Module
            public interface MyModuleFoo{
                @Provides @Named("foo") static Simple foo() { return new Simple("foo"); } 
            }
        """)

        givenJavaSource("test.TestComponent", """
            import javax.inject.Named;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
            @Component(modules = {MyModuleHello.class, MyModuleBye.class})
            public interface TestComponent {
                @Named("hello") Simple hello();
                @Named("bye") Simple bye();
                @Named("foo") Simple foo();
            }
        """)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan

            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c == c) { "Equality test" }
                assert(c.hello().value == "hello")
                assert(c.bye().value == "bye")
                assert(c.foo().value == "foo")
            } 
        """)

        compileRunAndValidate()
    }

    @Test
    fun `check @Provides used instead of @Inject constructor if exists`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class Impl(val value: Int)
            class Wrapper @Inject constructor(val i: Impl)

            @Module
            object MyModule {
                @Provides fun impl() = Impl(1)
                @Provides fun wrapper() = Wrapper(Impl(2))          
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun impl(): Impl
                fun wrapper(): Wrapper
            }

            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c.impl().value == 1)
                assert(c.wrapper().i.value == 2)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `thread assertions`() {
        givenOption(StringOption.ThreadCheckerClassName, "test.MyThreadAssertions")
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import java.lang.AssertionError
            import java.util.concurrent.*
            import javax.inject.*
            
            object MyThreadAssertions {
                var mainTid: Long = 0
                fun assertThreadAccess() {
                    if (Thread.currentThread().id != mainTid) {
                        throw AssertionError("Access on non-main thread")
                    }
                }
            }
            
            @Singleton class MyClassA @Inject constructor()
            @Singleton class MyClassB @Inject constructor()
            @Singleton class MyClassC @Inject constructor()
            /*un-scoped*/ class MyClassD @Inject constructor()
            
            @Component @Singleton interface MySTComponent {
                fun getA(): Lazy<MyClassA>
                fun getB(): MyClassB
                fun getC(): MyClassC
                fun getD(): Lazy<MyClassD>
            }

            fun test() {
                MyThreadAssertions.mainTid = Thread.currentThread().id
                val c: MySTComponent = Yatagan.create(MySTComponent::class.java)            
                run {
                    c.getC()  // Create ClassC ahead of time
                    val d1 = c.getD()
                    d1.get()  // Create ClassD inside d1 holder.
                    val d2 = c.getD()

                    val executor = Executors.newFixedThreadPool(2)
                    val t1 = executor.submit { 
                        try {
                            c.getA().get()
                            throw IllegalStateException("Test failed")
                        } catch (_: AssertionError) {}
                        c.getC()  // Shouldn't throw as ClassC is already 
                        d1.get()  // Shouldn't throw as object in d1 is pre-created
                        try {
                           d2.get()
                           throw IllegalStateException("Test failed")
                        } catch (_: AssertionError) {}
                    }
                    val t2 = executor.submit {
                        try {
                            c.getB()
                            throw IllegalStateException("Test failed")
                        } catch (_: AssertionError) {}
                        c.getC() // Shouldn't throw as ClassC is already created
                        d1.get()  // Shouldn't throw as object in d1 is pre-created
                    }
                    t1.get()
                    t2.get()

                    // On main:
                    c.getA().get()
                    c.getB()
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid thread checker - missing method`() {
        givenOption(StringOption.ThreadCheckerClassName, "test.InvalidThreadChecker4")
        givenKotlinSource("test.InvalidThreadChecker4", """
            object InvalidThreadChecker4 {
                // Missing assertThreadAccess method
            }
        """.trimIndent()
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Module
            object TestModule {
                @Provides fun provideString(): String = "test"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun getString(): String
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid thread checker - invalid method`() {
        givenOption(StringOption.ThreadCheckerClassName, "test.InvalidThreadChecker5")
        givenJavaSource("test.InvalidThreadChecker5", """
            public class InvalidThreadChecker5 {
                // Non-static method with parameter and package-private visibility
                void assertThreadAccess(String param) {
                    // This method is not static, has a parameter, and has package-private visibility
                }
            }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Module
            object TestModule {
                @Provides fun provideString(): String = "test"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun getString(): String
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid thread checker - missing class`() {
        givenOption(StringOption.ThreadCheckerClassName, "test.NonExistentThreadChecker")

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Module
            object TestModule {
                @Provides fun provideString(): String = "test"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun getString(): String
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}
