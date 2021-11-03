package com.yandex.daggerlite.testing

import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class CoreBindingsKotlinTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `basic component - @Module object`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        @Module
        object MyModule {
          @Provides
          fun provides(): Api = Impl()
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Module object @JvmStatic @Provides`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        @Module
        object MyModule {
            @JvmStatic
            @Provides fun provides(): Api = Impl()
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class]) @Singleton
            interface TestComponent {
                fun get(): Api;
                fun getProvider(): Provider<Api>;
                fun getLazy(): Lazy<Api>;
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Module with companion object`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        @Module
        interface MyModule {
            companion object {
                @Provides
                fun provides(): Api = Impl()
            }
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - simple kotlin companion object @JvmStatic @Provides`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        @Module
        interface MyModule {
            companion object {
                @JvmStatic
                @Provides 
                fun provides(): Api = Impl() 
            }
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class]) @Singleton
            interface TestComponent {
                fun get(): Api;
                fun getProvider(): Provider<Api>;
                fun getLazy(): Lazy<Api>;
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Provides with dependencies in companion`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource("test.BaseModule", """
            open class BaseModule {
                @Provides
                fun get(): Api = Impl()
            } 
        """
        )

        givenKotlinSource(
            "test.MyModule", """
        @Module
        interface MyModule {
            companion object : BaseModule()
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class]) @Singleton
            interface TestComponent {
                fun get(): Api;
                fun getProvider(): Provider<Api>;
                fun getLazy(): Lazy<Api>;
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Module instance`() {
        givenKotlinSource("test.Api", """interface Api { val id: Int }""")

        givenKotlinSource(
            "test.MyModule", """
        @Module
        class MyModule(private val myId: Int) {
          @Provides
          fun provides(): Api = object : Api { override val id get() = myId }
        }
        """
        )
        givenKotlinSource(
            "test.TestComponent", """
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                @Component.Factory
                interface Factory {
                    fun create(module: MyModule): TestComponent
                }
            }
        """
        )

        givenKotlinSource("test.TestCase", """
            fun test() {
                val m = MyModule(52)
                val c = DaggerTestComponent.factory().create(m)
                assert(c.get().id == 52)
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }

    @Test
    fun `basic component - properties as entry-points`() {
        givenKotlinSource("test.TestCase", """
            interface Api {}
            class Impl : Api

            @Module
            object MyModule {
              @Provides
              @JvmStatic
              fun api(): Api = Impl()
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                val api: test.Api
                val provider: Provider<test.Api>
                val lazy: Lazy<test.Api>
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    @Ignore("Fix in ksp")
    fun `basic component - @Provides property`() {
        givenKotlinSource("test.TestCase", """
            interface Api
            class Impl : Api

            @Module
            object MyModule {
                @get:Provides
                val api: Api = Impl()
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun api(): test.Api
                fun provider(): Provider<test.Api>
                fun lazy(): Lazy<test.Api>
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component with factory - BindsInstance`() {
        givenJavaSource("test.MyClass", """
        public class MyClass {}
        """)
        givenKotlinSource("test.TestComponent", """
        @Component
        interface TestComponent {
            fun get(): MyClass
            val provider: Provider<MyClass>
            val lazy: Lazy<MyClass>
            
            @Component.Factory
            interface Factory {
               fun create(@BindsInstance instance: MyClass): TestComponent 
            }
        }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - provide Any`() {
        givenKotlinSource("test.MyModule", """
            @Module
            object MyModule {
                @Provides
                fun provides(): Any {
                    return "object";
                }
            }
        """)

        givenKotlinSource("test.TestComponent", """
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): Any;
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic compoonent - provide list of strings`() {
        givenKotlinSource("test.MyModule", """
            @Module
            object MyModule {
                @Provides
                fun provides(): List<String> = listOf()
            }
        """
        )

        givenKotlinSource("test.TestComponent", """
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): List<String>;
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }
}

