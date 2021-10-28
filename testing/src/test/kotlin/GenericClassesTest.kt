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
            @Module interface MyModule {
                @Provides static MyClass<Integer> getMyClassInt() {
                    return new MyClass<>();
                }
            }
        """)
        givenJavaSource("test.TestComponent", """
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> get();
                Provider<MyClass<Integer>> getProvider();
                Lazy<MyClass<Integer>> getLazy();
            }
        """)

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }

    @Test
    fun `a few simple parameterized classes`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            @Module interface MyModule {
                @Provides static MyClass<Integer> getMyClassInt() {
                    return new MyClass<>();
                }
                
                @Provides static MyClass<Boolean> getMyClassBool() {
                    return new MyClass<>();
                }
            }
        """)
        givenJavaSource("test.TestComponent", """
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> getInt();
                Provider<MyClass<Integer>> getProviderInt();
                MyClass<Boolean> getBool();
                Provider<MyClass<Boolean>> getProviderBool();
            }
        """)

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }

    @Test
    fun `generic is parametrized`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")
        givenJavaSource("test.MySecondClass", """public class MySecondClass <T> {}""")

        givenJavaSource("test.MyModule", """
            @Module interface MyModule {
                @Provides static MyClass<MySecondClass<Object>> getMyClassObj() {
                    return new MyClass<>();
                }
                
                @Provides static MyClass<MySecondClass<String>> getMyClassStr() {
                    return new MyClass<>();
                }
            }
        """)
        givenJavaSource("test.TestComponent", """
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<MySecondClass<Object>> getObj();
                Provider<MyClass<MySecondClass<Object>> > getProviderInt();
                MyClass<MySecondClass<String>>  getStr();
                Provider<MyClass<MySecondClass<String>> > getProviderBool();
            }
        """)

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }

    @Test
    fun `generic parameters are empty`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            @Module interface MyModule {
                @Provides static MyClass getMyClassObj() {
                    return new MyClass<>();
                }
                 }
        """)
        givenJavaSource("test.TestComponent", """
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass get();
            }
        """)

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }
}