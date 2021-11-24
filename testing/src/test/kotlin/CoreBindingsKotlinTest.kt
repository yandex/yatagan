package com.yandex.daggerlite.testing

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
        givenKotlinSource("test.ExplicitImpl", """class ExplicitImpl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.daggerlite.Provides
        import com.yandex.daggerlite.Module

        @Module
        object MyModule {
          @Provides
          fun provides(): Api = Impl()
          
          @Provides
          @JvmStatic
          fun providesExplicit() = ExplicitImpl()
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                fun getExplicit(): test.ExplicitImpl
                fun getProviderExplicit(): Provider<test.ExplicitImpl>
                fun getLazyExplicit(): Lazy<test.ExplicitImpl>
            }
        """.trimIndent()
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Module with companion object`() {
        givenKotlinSource("test.Api", """interface Api {}""")
        givenKotlinSource("test.Impl", """class Impl : Api""")
        givenKotlinSource("test.ExplicitImpl", """class ExplicitImpl""")

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.daggerlite.Provides
        import com.yandex.daggerlite.Module

        @Module
        interface MyModule {
            companion object {
                @Provides
                fun provides(): Api = Impl()

                @Provides
                @JvmStatic
                fun providesExplicit() = ExplicitImpl()
            }
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                fun getExplicit(): test.ExplicitImpl
                fun getProviderExplicit(): Provider<test.ExplicitImpl>
                fun getLazyExplicit(): Lazy<test.ExplicitImpl>
            }
        """.trimIndent()
        )

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
            import com.yandex.daggerlite.Provides

            open class BaseModule {
                @Provides
                fun get(): Api = Impl()
            } 
        """.trimIndent()
        )

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.daggerlite.Module

        @Module
        interface MyModule {
            companion object : BaseModule()
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider
            import javax.inject.Singleton

            @Component(modules = [MyModule::class]) @Singleton
            interface TestComponent {
                fun get(): Api;
                fun getProvider(): Provider<Api>;
                fun getLazy(): Lazy<Api>;
            }
        """.trimIndent())

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
        import com.yandex.daggerlite.Provides
        import com.yandex.daggerlite.Module

        @Module
        class MyModule(private val myId: Int) {
          @Provides
          fun provides(): Api = object : Api { override val id get() = myId }
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                @Component.Builder
                interface Factory {
                    fun create(module: MyModule): TestComponent
                }
            }
        """.trimIndent()
        )

        givenKotlinSource("test.TestCase", """
            fun test() {
                val m = MyModule(52)
                val c = DaggerTestComponent.factory().create(m)
                assert(c.get().id == 52)
            }
        """.trimIndent())

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
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module

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
        """.trimIndent())

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - @Provides property`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Provider
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module

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
        """.trimIndent())

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component with factory - BindsInstance`() {
        givenJavaSource("test.MyClass", """
        public class MyClass {}
        """.trimIndent())
        givenKotlinSource("test.TestComponent", """
        import javax.inject.Provider
        import com.yandex.daggerlite.Component
        import com.yandex.daggerlite.BindsInstance
        import com.yandex.daggerlite.Lazy

        @Component
        interface TestComponent {
            fun get(): MyClass
            val provider: Provider<MyClass>
            val lazy: Lazy<MyClass>
            
            @Component.Builder
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
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module

            @Module
            object MyModule {
                @Provides
                fun provides(): Any {
                    return "object";
                }
            }
        """)

        givenKotlinSource("test.TestComponent", """
            import com.yandex.daggerlite.Component

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
    fun `basic component - consume Lazy (wildcard type test)`() {
        givenKotlinSource("test.MyModule", """
            import com.yandex.daggerlite.Lazy
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Provides
            import javax.inject.Inject

            class ClassA @Inject constructor(f: Lazy<ClassB>)
            class ClassB @Inject constructor()
            class ClassC

            @Module
            object MyModule {
                @Provides fun classC() = ClassC() 
                @Provides fun classB(f: Lazy<ClassC>): Any = f.get()
            }
        """.trimIndent())

        givenKotlinSource("test.TestComponent", """
            import com.yandex.daggerlite.Component

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): ClassA
                fun b(): Any
            }
        """.trimIndent())

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic compoonent - provide list of strings`() {
        givenKotlinSource("test.MyModule", """
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module

            @Module
            object MyModule {
                @Provides
                fun provides(): List<String> = listOf()
            }
        """
        )

        givenKotlinSource("test.TestComponent", """
            import com.yandex.daggerlite.Component

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

    @Test
    fun `basic component - universal builder support + variance test`() {
        givenKotlinSource("test.TestComponent", """
            import javax.inject.Inject
            import javax.inject.Named
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.BindsInstance

            class MyClass @Inject constructor (
                    @Named("foo") o1: Object,
                    @Named("bar") o2: Object,
                    @Named("baz") o3: Object,
                    foos: Set<Foo>,
                    bars: MutableSet<Bar>,
            )
            interface Foo
            interface Bar

            interface CreatorBase {
                @BindsInstance
                fun setFoo(@Named("foo") obj: Any): CreatorBase
            }

            @Component
            interface TestComponent {
                fun get(): MyClass
                val foos: MutableSet<out Foo>
                val bars: Set<out Bar>
                val bars2: Set<Bar>
                
                @Component.Builder
                interface Creator : CreatorBase {
                    @BindsInstance
                    fun setBar(@Named("bar") obj: Any): Creator
                    
                    @BindsInstance
                    fun setSetOfFoo(foos: Set<Foo>): Creator
                    
                    @BindsInstance
                    fun setSetOfBar(bars: MutableSet<Bar>): Creator
                    
                    fun create(
                        @BindsInstance @Named("baz") obj: Any,
                    ): TestComponent
                }
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic members inject`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Module
            
            import javax.inject.Inject
            import javax.inject.Named
            
            class ClassA @Inject constructor()
            class ClassB @Inject constructor()
            
            @Module
            interface MyModule {
                @Named("hello") @Binds fun classAHello(i: ClassA): ClassA
                @Named("bye") @Binds fun classABye(i: ClassA): ClassA
            }
            
            class Foo {
                @set:Inject @Named("hello")
                lateinit var helloA: ClassA
                @set:Inject @Named("bye")
                lateinit var bye: ClassA
                
                lateinit var b: ClassB
                    private set
                private lateinit var _a: ClassA
                
                @Inject
                fun setB(i: ClassB) { b = i }
                
                var a: ClassA 
                    get() = _a 
                    @Inject
                    set(value) { _a = value }
            }
            
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun injectFoo(foo: Foo)
            }
            
            fun test() {
                val foo = Foo()
                DaggerTestComponent().injectFoo(foo)
                foo.helloA; foo.bye; foo.b; foo.a
            }""".trimIndent())
        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }
}

