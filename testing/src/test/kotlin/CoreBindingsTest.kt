package com.yandex.daggerlite.testing

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class CoreBindingsTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    private lateinit var classes: SourceSet
    private lateinit var apiImpl: SourceSet

    @Before
    fun setUp() {
        classes = givenSourceSet {
            givenJavaSource(
                "test.MyScopedClass", """
            import javax.inject.Inject;
            import javax.inject.Singleton;
            
            @Singleton public class MyScopedClass {
                @Inject public MyScopedClass() {}
            }
        """.trimIndent()
            )

            givenJavaSource(
                "test.MySimpleClass", """
            import javax.inject.Inject;
            
            public class MySimpleClass {
                @Inject public MySimpleClass(MyScopedClass directDep) {}
            }
        """.trimIndent()
            )
        }

        apiImpl = givenSourceSet {
            givenJavaSource(
                "test.Api", """
        public interface Api {}    
        """.trimIndent()
            )
            givenJavaSource(
                "test.Impl", """
        import javax.inject.Inject;
            
        public class Impl implements Api {
          @Inject public Impl() {}
        }
        """.trimIndent()
            )
        }
    }

    @Test
    fun `basic component - direct, Provider and Lazy entry points`() {
        useSourceSet(classes)

        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Component;
            import javax.inject.Provider;
            import com.yandex.daggerlite.Lazy;
                        
            @Component @Singleton
            public interface TestComponent {
                MySimpleClass getMySimpleClass();
                MyScopedClass getMyScopedClass();
                Provider<MySimpleClass> getMySimpleClassProvider();
                Lazy<MySimpleClass> getMySimpleClassLazy();
                Provider<MyScopedClass> getMyScopedClassProvider();
                Lazy<MyScopedClass> getMyScopedClassLazy();
            }
        """.trimIndent()
        )

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - simple @Binds`() {
        useSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import com.yandex.daggerlite.Binds;
        import com.yandex.daggerlite.Module;
                    
        @Module
        public interface MyModule {
          @Binds Api bind(Impl i);
        }
        """.trimIndent()
        )

        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Component;
            import javax.inject.Provider;
            import com.yandex.daggerlite.Lazy;
            
            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """.trimIndent()
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Dagger
            fun test() {
                val c = Dagger.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """.trimIndent()
        )

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }

    @Test
    fun `basic component - simple @Provides`() {
        useSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import com.yandex.daggerlite.Provides;
        import com.yandex.daggerlite.Module;
        
        @Module
        public interface MyModule {
          @Provides static Api provides() {
            return new Impl();
          }
        }
        """
        )
        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Component;
            import javax.inject.Provider;
            import com.yandex.daggerlite.Lazy;

            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Dagger
            fun test() {
                val c = Dagger.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }

    @Test
    fun `basic component - @Provides with dependencies`() {
        useSourceSet(classes)
        useSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import javax.inject.Provider;
        import com.yandex.daggerlite.Provides;
        import com.yandex.daggerlite.Module;

        @Module
        public interface MyModule {
          @Provides static Api provides(Provider<MyScopedClass> dep, MySimpleClass dep2) {
            return new Impl();
          }
        }
        """
        )
        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Component;
            import javax.inject.Provider;
            import com.yandex.daggerlite.Lazy;

            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Dagger
            fun test() {
                val c = Dagger.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }

    @Test(timeout = 10_000)
    fun `basic component - cyclic reference with Provider edge`() {
        useSourceSet(classes)
        useSourceSet(apiImpl)

        givenJavaSource("test.Classes", """
        import javax.inject.Inject;
        import javax.inject.Provider;

        class MyClassA { public @Inject MyClassA(MyClassB dep) {} }
        class MyClassB { public @Inject MyClassB(Provider<MyClassA> dep) {} }
        """)
        givenJavaSource("test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Component;
            
            @Component @Singleton
            public interface TestComponent {
                MyClassA get();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - add imports to generated component`() {
        givenJavaSource("utils.MySimpleClass", """ 
            import javax.inject.Inject;

            public class MySimpleClass {
                @Inject
                public MySimpleClass() {}
            }
        """)

        givenJavaSource("test.MyProvider", """
            import javax.inject.Inject;
            import utils.MySimpleClass;
            
            public class MyProvider {
                @Inject
                public MyProvider(MySimpleClass i) {}
            }
        """
        )

        givenJavaSource("test.TestComponent", """
            import com.yandex.daggerlite.Component;
            
            @Component
            interface TestComponent {
                MyProvider get();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - module includes inherited methods`() {
        useSourceSet(apiImpl)

        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Binds;

            @Module
            interface MyModule {
                @Binds
                Api binds(Impl i);
            }
        """)

        givenJavaSource("test.MySubModule", """
            import com.yandex.daggerlite.Module;
            
            @Module
            interface MySubModule extends MyModule {}
        """)

        givenJavaSource("test.TestComponent", """
            import com.yandex.daggerlite.Component;

            @Component(modules = MySubModule.class)
            public interface TestComponent {
                Api get();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - provide primitive type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Module
            public interface MyModule {
                @Provides
                static int provides() {
                    return 1;
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
            import javax.inject.Provider;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Lazy;
            
            @Component(modules = MyModule.class)
            public interface TestComponent {
                int get();
                Lazy<Integer> getIntLazy();
                Provider<Integer> getIntProvider();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - convert class to primitive type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Module
            public interface MyModule {
                @Provides
                static Integer provides() {
                    return 1;
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
            import com.yandex.daggerlite.Component;

            @Component(modules = MyModule.class)
            public interface TestComponent {
                int get();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - provide array type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;
            
            @Module
            public interface MyModule {
                @Provides
                static int[] providesIntArray() {
                    return new int[] { 228, };
                }
                
                @Provides
                static Double[] providesDoubleArray() {
                    return new Double[] { 7.40, };
                }
                
                @Provides
                static String[] providesStringArray() {
                    return new String[] { "foo", "bar" };
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
            import javax.inject.Inject;
            import javax.inject.Provider;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Lazy;
            
            class Consumer<T> {
                @Inject
                public Consumer(int[] i1, Provider<int[]> i2, T[] i3, Provider<T[]> i4, String[] i5,
                                Provider<String[]> i6) {}
            }
            
            @Component(modules = MyModule.class)
            public interface TestComponent {
                int[] getInt();
                Lazy<int[]> getIntLazy();
                Provider<int[]> getIntProvider();

                Provider<Double[]> getDoubleProvider();
                Double[] getDouble();
                
                String[] getString();
                Lazy<String[]> getStringLazy();
                Provider<String[]> getStringProvider();
                
                Consumer<Double> c();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - provide Object`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Module;

            @Module
            public interface MyModule {
                @Provides
                static Object provides() {
                    return "object";
                }
            }
        """.trimIndent())

        givenJavaSource("test.TestComponent", """
            import com.yandex.daggerlite.Component;
            
            @Component(modules = MyModule.class)
            public interface TestComponent {
                Object get();
            }
        """.trimIndent())

        compilesSuccessfully {
            generatesJavaSources("test.Dagger\$TestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic members inject`() {
        givenJavaSource("test.TestCase", """
            import com.yandex.daggerlite.Binds;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Module;
            
            import javax.inject.Inject;
            import javax.inject.Named;
            
            class ClassA { @Inject ClassA() {} }
            class ClassB { @Inject ClassB() {} }
            
            @Module
            interface MyModule {
                @Named("hello") @Binds ClassA classAHello(ClassA i);
                @Named("bye") @Binds ClassA classABye(ClassA i);
            }
            
            class Foo {
                @Inject @Named("hello")
                public ClassA helloA;
                
                private ClassA bye;
                private ClassB b;
                
                @Inject
                public void setClassB(ClassB classB) { b = classB; }
            
                @Inject @Named("bye")
                public void setClassA(ClassA classA) { bye = classA; }
            }
            
            @Component(modules = {MyModule.class})
            interface TestComponent {
                void injectFoo(Foo foo);
            }
        """.trimIndent())
        compilesSuccessfully {
            withNoWarnings()
        }
    }

    @Test
    fun `trivially constructable module`() {
        givenJavaSource("test.TestCase", """
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Provides;
            
            @Module
            class MyModule {
                private final Object mObj = new Object();
                @Provides
                public Object provides() { return mObj; }
            }
            
            @Component(modules = MyModule.class)
            interface MyComponent {
                Object get();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.Dagger\$MyComponent")
        }
    }

    @Test
    fun `type parameters and multi-bindings`() {
        givenJavaSource("test.TestCase", """
            import java.util.Collections;
            import java.util.Collection;
            import java.util.List;
            import javax.inject.Provider;
            import javax.inject.Inject;
            import javax.inject.Singleton;
            import com.yandex.daggerlite.Binds;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Component;
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.IntoList;
            
            class Deferred<T> {
                @Inject Deferred(Provider<T> provider) { }
            }

            interface MySpecificDeferredEvent {}

            class MyClass1 implements MySpecificDeferredEvent { @Inject MyClass1 () {} }
            @Singleton class MyClass2 implements MySpecificDeferredEvent { @Inject MyClass2 () {} }
            class MyClass3 implements MySpecificDeferredEvent { @Inject MyClass3 () {} }

            @Module
            interface MyModule {
                @IntoList @Binds Deferred<? extends MySpecificDeferredEvent> foo1(Deferred<MyClass1> i);
                @IntoList @Binds Deferred<? extends MySpecificDeferredEvent> foo2(Deferred<MyClass2> i);
                @IntoList @Binds Deferred<? extends MySpecificDeferredEvent> foo3(Deferred<MyClass3> i);
                @IntoList @Binds Deferred<? extends MySpecificDeferredEvent> foo4();
                @IntoList @Provides static Deferred<? extends MySpecificDeferredEvent> foo5(Provider<MyClass3> p) {
                    return new Deferred<>(p);
                }
                @IntoList(flatten = true) @Singleton
                @Provides static Collection<Deferred<? extends MySpecificDeferredEvent>> collection1() {
                    return Collections.emptyList();
                }
                @IntoList(flatten = true)
                @Provides static Collection<Deferred<? extends MySpecificDeferredEvent>> collection2() {
                    return Collections.emptyList();
                }
            }
            
            @Singleton
            @Component(modules = MyModule.class)
            interface MyComponent {
                List<Deferred<? extends MySpecificDeferredEvent>> deferred();
                Provider<List<Deferred<? extends MySpecificDeferredEvent>>> deferredProvider();
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
        }
    }

    @Test
    fun `creator inputs are null-checked`() {
        givenJavaSource("test.MyComponentBase", """
            import com.yandex.daggerlite.BindsInstance;
            
            public interface MyComponentBase {
                char getChar();
                double getDouble();
                int getInt();
                long getLong();
                interface Builder<T extends MyComponentBase> {
                    @BindsInstance void setChar(char c);
                    @BindsInstance void setDouble(Double d);
                    T create(@BindsInstance int i1, @BindsInstance long i2);
                }
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import kotlin.test.*

            @Component interface MyComponent : MyComponentBase {
                @Component.Builder interface Builder : MyComponentBase.Builder<MyComponent>
            }

            fun test() {
                fun builder() = Dagger.builder(MyComponent.Builder::class.java)
                // Creates normally
                builder().run { 
                    setChar('A')
                    setDouble(0.0)
                    create(1, 2L)
                }
                // Explicit null
                builder().run { 
                    setChar('A')
                    setDouble(null)
                    val e = assertFailsWith<AssertionError> { create(1, 2L) }
                    assertEquals("Component input is null or unspecified", e.message)
                }
                // Input omitted
                builder().run { 
                    setDouble(0.0)
                    val e = assertFailsWith<AssertionError> { create(1, 2L) }
                    assertEquals("Component input is null or unspecified", e.message)
                }
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `provision results are null-checked`() {
        givenJavaSource("test.MyModule", """
            @com.yandex.daggerlite.Module
            public interface MyModule {
                @com.yandex.daggerlite.Provides
                static Integer provideNullInt() {
                    return null;
                }
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import kotlin.test.*

            @Component(modules = [MyModule::class])
            interface TestComponent {
                val integer: Int
            }

            fun test() {
                val c = Dagger.create(TestComponent::class.java)
                val e = assertFailsWith<AssertionError> { 
                    c.integer
                }
                assertEquals("Provision result is null", e.message)
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }
}

