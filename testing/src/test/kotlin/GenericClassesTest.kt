package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class GenericClassesTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `simple parameterized class`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
           
            @Module interface MyModule {
                @Provides static MyClass<Integer> getMyClassInt() {
                    return new MyClass<>();
                }
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import javax.inject.Provider;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Lazy;
            
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> get();
                Provider<MyClass<Integer>> getProvider();
                Lazy<MyClass<Integer>> getLazy();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.Dagger\$TestComponent")
        }
    }

    @Test
    fun `a few simple parameterized classes`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;

            @Module interface MyModule {
                @Provides static MyClass<Integer> getMyClassInt() {
                    return new MyClass<>();
                }
                
                @Provides static MyClass<Boolean> getMyClassBool() {
                    return new MyClass<>();
                }
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import javax.inject.Provider;
            import com.yandex.daggerlite.Component;

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> getInt();
                Provider<MyClass<Integer>> getProviderInt();
                MyClass<Boolean> getBool();
                Provider<MyClass<Boolean>> getProviderBool();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.Dagger\$TestComponent")
        }
    }

    @Test
    fun `generic is parametrized`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")
        givenJavaSource("test.MySecondClass", """public class MySecondClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;

            @Module interface MyModule {
                @Provides static MyClass<MySecondClass<Object>> getMyClassObj() {
                    return new MyClass<>();
                }
                
                @Provides static MyClass<MySecondClass<String>> getMyClassStr() {
                    return new MyClass<>();
                }
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import javax.inject.Provider;
            import com.yandex.daggerlite.Component;      

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<MySecondClass<Object>> getObj();
                Provider<MyClass<MySecondClass<Object>> > getProviderInt();
                MyClass<MySecondClass<String>>  getStr();
                Provider<MyClass<MySecondClass<String>> > getProviderBool();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.Dagger\$TestComponent")
        }
    }

    @Test
    fun `generic parameters are empty`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;        

            @Module interface MyModule {
                @Provides static MyClass getMyClassObj() {
                    return new MyClass<>();
                }
                 }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import com.yandex.daggerlite.Component;

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass get();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.Dagger\$TestComponent")
        }
    }

    @Test
    fun `jvm wildcard annotations are honored`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            interface Api
            class Impl : Api

            typealias MyHandler = (id: String) -> Unit
            typealias MyHandler2 = @JvmSuppressWildcards (api: Api) -> Unit

            class Consumer @Inject constructor(
                apis0: @JvmSuppressWildcards List<List<Api>>,
                apis: List<@JvmSuppressWildcards List<Api>>,
                impls0: @JvmSuppressWildcards List<List<Impl>>,
                impls: List<@JvmSuppressWildcards List<Impl>>,
                handler: @JvmSuppressWildcards MyHandler,
                handler2: MyHandler2,
            )
            
            @Module class TestModule {
                @Provides fun someListOfApi(): List<List<Api>> {
                    return emptyList()
                }

                @Provides fun someListOfImpl(): List<List<Impl>> {
                    return emptyList()
                }

                @Provides fun handler(): MyHandler = {}
                @Provides fun handler2(): MyHandler2 = {}
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                val apis: List<List<Api>>
                val impls: List<List<Impl>>
                val consumer: Consumer
                val handler: MyHandler
                val handler2: MyHandler2
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
        }
    }

    @Test
    fun `java raw types`() {
        precompile(givenSourceSet {
            givenJavaSource("test.TestComponentBase", """
                public interface TestComponentBase {
                    interface Api {}
                    class Injector<T extends Api> {}

                        @SuppressWarnings({"rawtypes", "RedundantSuppression"})
                        void inject(Injector i);
                }
            """.trimIndent())
        })

        givenJavaSource("test.TestCase", """
            import com.yandex.daggerlite.Component;
            import javax.inject.Inject;
            
            interface MyApi {}
            class MyGenericClass<T extends MyApi> { @Inject public MyGenericClass() {} }
            class MyGenericClass2<T> { @Inject public MyGenericClass2() {} }

            @SuppressWarnings({"rawtypes", "RedundantSuppression"})
            @Component
            interface TestComponent extends TestComponentBase {
                MyGenericClass get();
                MyGenericClass2 get2();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
        }
    }
}