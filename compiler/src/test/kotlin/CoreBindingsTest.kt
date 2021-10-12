package com.yandex.dagger3.compiler

import kotlin.test.Test

class CoreBindingsTest : CompileTestBase() {
    private val classes = givenSourceSet {
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

    private val apiImpl = givenSourceSet {
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

    @Test
    fun `basic component - direct, Provider and Lazy entry points`() {
        useSourceSet(classes)

        givenJavaSource(
            "test.TestComponent", """
            @Component
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

        assertCompilesSuccessfully {
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
            @Component(modules = {MyModule.class})
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        assertCompilesSuccessfully {
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
            @Component(modules = {MyModule.class})
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        assertCompilesSuccessfully {
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
            @Component(modules = {MyModule.class})
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        assertCompilesSuccessfully {
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
            @Component(modules = {MyModule.class})
            public interface TestComponent {
                @Named("hello") Api get();
                @Named("hello") Provider<Api> getProvider();
                @Named("hello") Lazy<Api> getLazy();
            }
        """
        )

        assertCompilesSuccessfully {
            generatesJavaSources("test.DaggerTestComponent")
            withNoWarnings()
        }
    }
}

