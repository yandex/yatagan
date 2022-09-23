package com.yandex.daggerlite.testing

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
            import com.yandex.daggerlite.Dagger

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
                val c = Dagger.create(TestComponent::class.java)
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
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;

            @Module(includes = {MyModuleBye.class, MyModuleFoo.class})
            public interface MyModuleHello {
                @Provides @Named("hello") static Simple hello() { return new Simple("hello"); } 
            }
        """)

        givenJavaSource("test.MyModuleBye", """
            import javax.inject.Named;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Module(includes = {MyModuleFoo.class})
            public interface MyModuleBye{
                @Provides @Named("bye") static Simple bye() { return new Simple("bye"); } 
            }
        """)

        givenJavaSource("test.MyModuleFoo", """
            import javax.inject.Named;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Module
            public interface MyModuleFoo{
                @Provides @Named("foo") static Simple foo() { return new Simple("foo"); } 
            }
        """)

        givenJavaSource("test.TestComponent", """
            import javax.inject.Named;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Component(modules = {MyModuleHello.class, MyModuleBye.class})
            public interface TestComponent {
                @Named("hello") Simple hello();
                @Named("bye") Simple bye();
                @Named("foo") Simple foo();
            }
        """)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Dagger

            fun test() {
                val c = Dagger.create(TestComponent::class.java)
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
            import com.yandex.daggerlite.*
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
                val c = Dagger.create(TestComponent::class.java)
                assert(c.impl().value == 1)
                assert(c.wrapper().i.value == 2)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `thread assertions`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            import kotlin.concurrent.thread
            
            @Singleton class MyClassA @Inject constructor()
            @Singleton class MyClassB @Inject constructor()
            
            @Component @Singleton interface MySTComponent {
                fun getA(): Lazy<MyClassA>
                fun getB(): MyClassB
            }

            fun test() {
                val c: MySTComponent = Dagger.create(MySTComponent::class.java)
                val mainTid = Thread.currentThread().id
                ThreadAssertions.asserter = ThreadAssertions.Asserter {
                    if (Thread.currentThread().id != mainTid) {
                        throw AssertionError("Access on non-main thread")
                    }
                }
                try {
                    var success = true
                    val t1 = thread {
                        try {
                            c.getA().get()
                            success = false
                        } catch (_: AssertionError) { }
                    }
                    val t2 = thread {
                        try {
                            c.getB()
                            success = false
                        } catch (_: AssertionError) { }
                    }
                    try {
                        c.getA().get()
                        c.getB()
                    } catch (_: AssertionError) {
                        success = false
                    }
                    t1.join()
                    t2.join()
                    
                    if (!success) {
                        throw AssertionError("Test failed")
                    }
                } finally {
                    ThreadAssertions.asserter = null
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}