package com.yandex.daggerlite.testing

import org.junit.Before
import org.junit.Ignore
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
            generatesJavaSources("test.DaggerTestComponent")
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
            fun test() {
                val c = DaggerTestComponent()
                assert(c.get() is Impl)
            }
        """.trimIndent()
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
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
            fun test() {
                val c = DaggerTestComponent()
                assert(c.get() is Impl)
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
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
            fun test() {
                val c = DaggerTestComponent()
                assert(c.get() is Impl)
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
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
            generatesJavaSources("test.DaggerTestComponent")
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
            generatesJavaSources("test.DaggerTestComponent")
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
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    @Ignore
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
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    @Ignore
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
            generatesJavaSources("test.DaggerTestComponent")
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
            generatesJavaSources("test.DaggerTestComponent")
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
            generatesJavaSources("test.DaggerMyComponent")
        }
    }
}

