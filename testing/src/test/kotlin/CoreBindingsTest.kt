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

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
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

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
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

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - qualified dependencies`() {
        useSourceSet(classes)
        useSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        @Module
        public interface MyModule {
          @Named("hello")
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
                @Named("hello") Api get();
                @Named("hello") Provider<Api> getProvider();
                @Named("hello") Lazy<Api> getLazy();
            }
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `basic component - multiple qualified dependencies`() {
        useSourceSet(apiImpl)

        givenJavaSource("test.MyModule", """
            @Module
            public interface MyModule {
              @Named("foo") @Singleton @Provides
              static Api providesFoo() { return new Impl(); }
              @Named("bar") @Singleton @Provides
              static Api providesApi() { return new Impl(); }
              @Named("quu") @Singleton @Provides
              static Api providesQuu() { return new Impl(); }
            }
        """)
        givenJavaSource("test.TestComponent", """
            @Component(modules = {MyModule.class}) 
            @Singleton
            public interface TestComponent {
                @Named("foo") Api getFoo();
                @Named("bar") Api getBar();
                @Named("quu") Api getQuu();
            }
        """)
        givenKotlinSource("test.TestCase", """
            fun test() {
                val c = DaggerTestComponent()
                assert(c.getFoo() === c.getFoo()); assert(c.getFoo() !== c.getBar())
                assert(c.getBar() === c.getBar()); assert(c.getBar() !== c.getQuu())
                assert(c.getQuu() === c.getQuu())
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
}

