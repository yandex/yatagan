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

import com.yandex.yatagan.testing.source_set.SourceSet
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
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
           
            @Module public interface MyModule {
                @Provides static MyClass<Integer> getMyClassInt() {
                    return new MyClass<>();
                }
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import javax.inject.Provider;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Lazy;
            
            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> get();
                Provider<MyClass<Integer>> getProvider();
                Lazy<MyClass<Integer>> getLazy();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `a few simple parameterized classes`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;

            @Module public interface MyModule {
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
            import com.yandex.yatagan.Component;

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<Integer> getInt();
                Provider<MyClass<Integer>> getProviderInt();
                MyClass<Boolean> getBool();
                Provider<MyClass<Boolean>> getProviderBool();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `generic is parametrized`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")
        givenJavaSource("test.MySecondClass", """public class MySecondClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;

            @Module public interface MyModule {
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
            import com.yandex.yatagan.Component;      

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass<MySecondClass<Object>> getObj();
                Provider<MyClass<MySecondClass<Object>> > getProviderInt();
                MyClass<MySecondClass<String>>  getStr();
                Provider<MyClass<MySecondClass<String>> > getProviderBool();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `generic parameters are empty`() {
        givenJavaSource("test.MyClass", """public class MyClass <T> {}""")

        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;        

            @Module public interface MyModule {
                @Provides static MyClass getMyClassObj() {
                    return new MyClass<>();
                }
                 }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;

            @Component(modules = MyModule.class) interface TestComponent {
                MyClass get();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `jvm wildcard annotations are honored`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
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

        compileRunAndValidate()
    }

    @Test
    fun `java raw types`() {
        givenPrecompiledModule(SourceSet {
            givenJavaSource("test.TestComponentBase", """
                public interface TestComponentBase {
                    interface Api {}
                    class Injector<T extends Api> {}

                    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
                    void inject(Injector i);
                }
            """.trimIndent())
        })

        givenJavaSource("test.MyGenericClass", """
            import javax.inject.Inject;

            interface MyApi {}
            public class MyGenericClass<T extends MyApi> { @Inject public MyGenericClass() {} }
        """.trimIndent())

        givenJavaSource("test.MyGenericClass2", """
            import javax.inject.Inject;

            public class MyGenericClass2<T> { @Inject public MyGenericClass2() {} }
        """.trimIndent())

        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;

            @SuppressWarnings({"rawtypes", "RedundantSuppression"})
            @Component
            public interface TestComponent extends TestComponentBase {
                MyGenericClass get();
                MyGenericClass2 get2();
            }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            fun test() {
                val c = com.yandex.yatagan.Yatagan.create(TestComponent::class.java)
                c.get()
                c.get2()
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}