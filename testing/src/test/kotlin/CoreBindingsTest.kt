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
            @Singleton public class MyScopedClass {
                @Inject public MyScopedClass() {}
            }
        """
            )

            givenJavaSource(
                "test.MySimpleClass", """
            public class MySimpleClass {
                @Inject public MySimpleClass(MyScopedClass directDep) {}
            }
        """
            )
        }

        apiImpl = givenSourceSet {
            givenJavaSource(
                "test.Api", """
        public interface Api {}    
        """
            )
            givenJavaSource(
                "test.Impl", """
        public class Impl implements Api {
          @Inject public Impl() {}
        }
        """
            )
        }
    }

    @Test
    fun `basic component - direct, Provider and Lazy entry points`() {
        useSourceSet(classes)

        givenJavaSource(
            "test.TestComponent", """
            @Component @Singleton
            public interface TestComponent {
                MySimpleClass getMySimpleClass();
                MyScopedClass getMyScopedClass();
                Provider<MySimpleClass> getMySimpleClassProvider();
                Lazy<MySimpleClass> getMySimpleClassLazy();
                Provider<MyScopedClass> getMyScopedClassProvider();
                Lazy<MyScopedClass> getMyScopedClassLazy();
            }
        """
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
        @Module
        public interface MyModule {
          @Binds Api bind(Impl i);
        }
        """
        )

        givenJavaSource(
            "test.TestComponent", """
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
    fun `basic component - simple @Provides`() {
        useSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
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
        class MyClassA { public @Inject MyClassA(MyClassB dep) {} }
        class MyClassB { public @Inject MyClassB(Provider<MyClassA> dep) {} }
        """)
        givenJavaSource("test.TestComponent", """
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
            public class MySimpleClass {
                @Inject
                public MySimpleClass() {}
            }
        """)

        givenJavaSource("test.MyProvider", """
            import utils.MySimpleClass;
            
            public class MyProvider {
                @Inject
                public MyProvider(MySimpleClass i) {}
            }
        """
        )

        givenJavaSource("test.TestComponent", """
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
            @Module
            interface MyModule {
                @Binds
                Api binds(Impl i);
            }
        """)

        givenJavaSource("test.MySubModule", """
            @Module
            interface MySubModule extends MyModule {}
        """)

        givenJavaSource("test.TestComponent", """
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
            @Module
            public interface MyModule {
                @Provides
                static int provides() {
                    return 1;
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
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
            @Module
            public interface MyModule {
                @Provides
                static Integer provides() {
                    return 1;
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
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
            @Module
            public interface MyModule {
                @Provides
                static Object provides() {
                    return "object";
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
            @Component(modules = MyModule.class)
            public interface TestComponent {
                Object get();
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }
}

