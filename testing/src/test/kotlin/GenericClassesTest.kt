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
}