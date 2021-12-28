package com.yandex.daggerlite.testing

import com.yandex.daggerlite.validation.impl.Strings
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
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module

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
                val c = DaggerTestComponent.create()
                assert(c.simple().value == "simple")
                assert(c.one().value == "one")
                assert(c.two().value == "two")
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { tc ->
                tc["test"](null)
            }
        }
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

        givenJavaSource("test.TestCase", """
            import javax.inject.Named;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;

            @Module(includes = {MyModuleBye.class, MyModuleFoo.class})
            interface MyModuleHello {
                @Provides @Named("hello") static Simple hello() { return new Simple("hello"); } 
            }
            @Module(includes = {MyModuleFoo.class})
            interface MyModuleBye{
                @Provides @Named("bye") static Simple bye() { return new Simple("bye"); } 
            }
            @Module
            interface MyModuleFoo{
                @Provides @Named("foo") static Simple foo() { return new Simple("foo"); } 
            }
            
            @Component(modules = {MyModuleHello.class, MyModuleBye.class})
            interface TestComponent {
                @Named("hello") Simple hello();
                @Named("bye") Simple bye();
                @Named("foo") Simple foo();
            }
        """)

        givenKotlinSource("test.TestCase", """
            fun test() {
                val c = DaggerTestComponent.create()
                assert(c.hello().value == "hello")
                assert(c.bye().value == "bye")
                assert(c.foo().value == "foo")
            } 
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `check @Provides used instead of @Inject constructor if exists`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module
            import javax.inject.Inject

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
                val c = DaggerTestComponent.create()
                assert(c.impl().value == 1)
                assert(c.wrapper().i.value == 2)
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withWarning(Strings.formatMessage(
                message = Strings.Warnings.`custom binding shadow @Inject constructor`(
                    target = "test.Wrapper", binding = "@Provides test.MyModule::wrapper(): test.Wrapper",
                ),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[entry-point] wrapper", "@Provides test.MyModule::wrapper(): test.Wrapper")
                ),
            ))
            withNoMoreWarnings()
            inspectGeneratedClass("test.TestCaseKt") { tc ->
                tc["test"](null)
            }
        }
    }
}