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
    @Ignore("TODO: support this")
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
}

