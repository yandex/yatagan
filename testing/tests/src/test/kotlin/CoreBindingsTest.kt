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
        classes = SourceSet {
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

        apiImpl = SourceSet {
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
        includeFromSourceSet(classes)

        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.yatagan.Component;
            import javax.inject.Provider;
            import com.yandex.yatagan.Lazy;
                        
            @Component @Singleton
            public interface TestComponent {
                MySimpleClass getMySimpleClass();
                MyScopedClass getMyScopedClass();
                Provider<MySimpleClass> getMySimpleClassProvider();
                Lazy<MySimpleClass> getMySimpleClassLazy();
                Provider<MyScopedClass> getMyScopedClassProvider();
                Lazy<MyScopedClass> getMyScopedClassLazy();
                
                default void someNonAbstractMethod() { }
            }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - simple @Binds`() {
        includeFromSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import com.yandex.yatagan.Binds;
        import com.yandex.yatagan.Module;
                    
        @Module
        public interface MyModule {
          @Binds Api bind(Impl i);
        }
        """.trimIndent()
        )

        givenJavaSource(
            "test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.yatagan.Component;
            import javax.inject.Provider;
            import com.yandex.yatagan.Lazy;
            
            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """.trimIndent()
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan
            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - simple @Provides`() {
        includeFromSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import com.yandex.yatagan.Provides;
        import com.yandex.yatagan.Module;
        
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
            import com.yandex.yatagan.Component;
            import javax.inject.Provider;
            import com.yandex.yatagan.Lazy;

            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan
            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - @Provides with dependencies`() {
        includeFromSourceSet(classes)
        includeFromSourceSet(apiImpl)

        givenJavaSource(
            "test.MyModule", """
        import javax.inject.Provider;
        import com.yandex.yatagan.Provides;
        import com.yandex.yatagan.Module;

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
            import com.yandex.yatagan.Component;
            import javax.inject.Provider;
            import com.yandex.yatagan.Lazy;

            @Component(modules = {MyModule.class}) @Singleton
            public interface TestComponent {
                Api get();
                Provider<Api> getProvider();
                Lazy<Api> getLazy();
            }
        """
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan
            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                assert(c.get() is Impl)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test(timeout = 10_000)
    fun `basic component - cyclic reference with Provider edge`() {
        includeFromSourceSet(classes)
        includeFromSourceSet(apiImpl)

        givenJavaSource("test.MyClassA", """
            public class MyClassA { public @javax.inject.Inject MyClassA(MyClassB dep) {} }
        """.trimIndent())
        givenJavaSource("test.MyClassB", """
            import javax.inject.Provider;
            public class MyClassB { public @javax.inject.Inject MyClassB(Provider<MyClassA> dep) {} }
        """.trimIndent())

        givenJavaSource("test.TestComponent", """
            import javax.inject.Singleton;
            import com.yandex.yatagan.Component;
            
            @Component @Singleton
            public interface TestComponent {
                MyClassA get();
            }
        """)

        compileRunAndValidate()
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
            import com.yandex.yatagan.Component;
            
            @Component
            interface TestComponent {
                MyProvider get();
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - module includes inherited methods`() {
        includeFromSourceSet(apiImpl)

        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Binds;

            @Module
            interface MyModule {
                @Binds
                Api binds(Impl i);
            }
        """)

        givenJavaSource("test.MySubModule", """
            import com.yandex.yatagan.Module;
            
            @Module
            interface MySubModule extends MyModule {}
        """)

        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;

            @Component(modules = MySubModule.class)
            public interface TestComponent {
                Api get();
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide primitive type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
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
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Lazy;
            
            @Component(modules = MyModule.class)
            public interface TestComponent {
                int get();
                Lazy<Integer> getIntLazy();
                Provider<Integer> getIntProvider();
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - convert class to primitive type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
            @Module
            public interface MyModule {
                @Provides
                static Integer provides() {
                    return 1;
                }
            }
        """)

        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;

            @Component(modules = MyModule.class)
            public interface TestComponent {
                int get();
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide array type`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            
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

        givenJavaSource("test.Consumer", """
            import javax.inject.Provider;
            import javax.inject.Inject;

            public class Consumer<T> {
                @Inject
                public Consumer(int[] i1, Provider<int[]> i2, T[] i3, Provider<T[]> i4, String[] i5,
                                Provider<String[]> i6) {}
            }
        """.trimIndent())

        givenJavaSource("test.TestComponent", """
            import javax.inject.Inject;
            import javax.inject.Provider;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Lazy;
            
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

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide Object`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;

            @Module
            public interface MyModule {
                @Provides
                static Object provides() {
                    return "object";
                }
            }
        """.trimIndent())

        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;
            
            @Component(modules = MyModule.class)
            public interface TestComponent {
                Object get();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `java component interface extends kotlin one with properties`() {
        givenPrecompiledModule(SourceSet {
            givenKotlinSource("mod.Precompiled", """
                import com.yandex.yatagan.*
                import javax.inject.*

                class SomeClass @Inject constructor()
                interface KotlinInterface {
                    val someClass: SomeClass
                }
            """.trimIndent())
        })
        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;
            import mod.KotlinInterface;
            @Component
            public interface TestComponent extends KotlinInterface {
                
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `java component declaration with builder and nested dependency`() {
        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;
            
            @Component(dependencies = {TestComponent.Dependency.class})
            public interface TestComponent {
                interface Dependency {
                    int provideInt();
                }
            
                @Component.Builder
                interface Builder {
                    TestComponent create(Dependency dep);
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic members inject`() {
        givenJavaSource("test.ClassA", """
            import javax.inject.Inject;
            public class ClassA { public @Inject ClassA() {} }
        """.trimIndent())
        givenJavaSource("test.ClassB", """
            import javax.inject.Inject;
            public class ClassB { public @Inject ClassB() {} }
        """.trimIndent())
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Binds;
            import javax.inject.Named;
            
            @Module
            public interface MyModule {
                @Named("hello") @Binds ClassA classAHello(ClassA i);
                @Named("bye") @Binds ClassA classABye(ClassA i);
            }
        """.trimIndent())
        givenJavaSource("test.Foo", """
            import javax.inject.Inject;
            import javax.inject.Named;

            public class Foo {
                @Inject @Named("hello")
                public ClassA helloA;
                
                private ClassA bye;
                private ClassB b;
                
                @Inject
                public void setClassB(ClassB classB) { b = classB; }
            
                @Inject @Named("bye")
                public void setClassA(ClassA classA) { bye = classA; }
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent", """
            import com.yandex.yatagan.Component;
            
            @Component(modules = {MyModule.class})
            interface TestComponent {
                void injectFoo(Foo foo);
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            fun test() {
                val c = com.yandex.yatagan.Yatagan.create(TestComponent::class.java)
                c.injectFoo(Foo())
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `trivially constructable module`() {
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            
            @Module
            public class MyModule {
                private final Object mObj = new Object();
                @Provides
                public Object provides() { return mObj; }
            }
        """.trimIndent())

        givenJavaSource("test.MyComponent", """
            import com.yandex.yatagan.Component;

            @Component(modules = MyModule.class)
            interface MyComponent {
                Object get();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `type parameters and multi-bindings`() {
        givenPrecompiledModule(SourceSet {
            givenKotlinSource("test.Deferred", """
                import javax.inject.*
                class Deferred<out T> @Inject constructor(val provider: Provider<out T>)
            """.trimIndent())
        })
        givenJavaSource("test.MyModule", """
            import java.util.Collection;
            import java.util.Collections;
            import javax.inject.Provider;
            import javax.inject.Singleton;
            import com.yandex.yatagan.Binds;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.IntoList;

            @Module
            public interface MyModule {
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
        """.trimIndent())
        givenJavaSource("test.MyClass1", """
            public class MyClass1 implements MySpecificDeferredEvent { public @javax.inject.Inject MyClass1 () {} }
        """.trimIndent())
        givenJavaSource("test.MyClass2", """
            @javax.inject.Singleton 
            public class MyClass2 implements MySpecificDeferredEvent { public @javax.inject.Inject MyClass2 () {} }
        """.trimIndent())
        givenJavaSource("test.MyClass3", """
            public class MyClass3 implements MySpecificDeferredEvent { public @javax.inject.Inject MyClass3 () {} }
        """.trimIndent())
//        givenJavaSource("test.Deferred", """
//            import javax.inject.Provider;
//            public class Deferred<T> { public @javax.inject.Inject Deferred (Provider<T> provider) {} }
//        """.trimIndent())
        givenJavaSource("test.MySpecificDeferredEvent", """
            interface MySpecificDeferredEvent {}
        """.trimIndent())

        givenJavaSource("test.MyComponent", """
            import java.util.Collections;
            import java.util.Collection;
            import java.util.List;
            import javax.inject.Provider;
            import javax.inject.Inject;
            import javax.inject.Singleton;
            import com.yandex.yatagan.Component;
    
            @Singleton
            @Component(modules = MyModule.class)
            interface MyComponent {
                List<Deferred<? extends MySpecificDeferredEvent>> deferred();
                Provider<List<Deferred<? extends MySpecificDeferredEvent>>> deferredProvider();
            }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            fun test() {
                val c = com.yandex.yatagan.Yatagan.create(MyComponent::class.java)
                c.deferred();
                c.deferredProvider().get();
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `creator inputs are null-checked`() {
        givenJavaSource("test.MyComponentBase", """
            import com.yandex.yatagan.BindsInstance;
            
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
            import com.yandex.yatagan.*

            @Component interface MyComponent : MyComponentBase {
                @Component.Builder interface Builder : MyComponentBase.Builder<MyComponent>
            }

            fun test() {
                fun builder() = Yatagan.builder(MyComponent.Builder::class.java)
                // Creates normally
                builder().run { 
                    setChar('A')
                    setDouble(0.0)
                    create(1, 2L)
                }
                // Explicit null
                builder().run {
                    setChar('A')
                    try {
                        // Implementations are free to throw on either setter or creation invocation. 
                        setDouble(null)
                        create(1, 2L)
                        throw AssertionError("Fail expected, but not occurred")
                    } catch (e: IllegalStateException) { 
                        // Ok
                    } catch (e: NullPointerException) {
                        // Ok
                    }
                }
                // Input omitted
                builder().run { 
                    setDouble(0.0)
                    try {
                        create(1, 2L)
                        throw AssertionError("Fail expected, but not occurred")
                    } catch (e: IllegalStateException) {
                        // Ok
                    }
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `provision results are null-checked`() {
        givenJavaSource("test.MyModule", """
            @com.yandex.yatagan.Module
            public interface MyModule {
                @com.yandex.yatagan.Provides
                static Integer provideNullInt() {
                    return null;
                }
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*

            @Component(modules = [MyModule::class])
            interface TestComponent {
                val integer: Int
            }

            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                try { 
                    c.integer
                    throw AssertionError("Fail expected, but not occurred")
                } catch (e: IllegalStateException) {
                    // Ok
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic assisted inject`() {
        givenJavaSource("test.ScopedDep", """
            import javax.inject.Inject;
            import javax.inject.Singleton;
            @Singleton
            public class ScopedDep {
                @Inject public ScopedDep() {}
            }
        """.trimIndent())
        givenJavaSource("test.UnscopedDep", """
            import javax.inject.Inject;
            public class UnscopedDep {
                @Inject public UnscopedDep() {}
            }
        """.trimIndent())
        givenJavaSource("test.BarFactory", """
            import com.yandex.yatagan.AssistedFactory;
            import com.yandex.yatagan.Assisted;
            @AssistedFactory
            public interface BarFactory {
                Bar buildBar(@Assisted("c2") int count2, @Assisted("c1") int count1, String value);
            }
        """.trimIndent())
        givenJavaSource("test.FooFactory", """
            import com.yandex.yatagan.AssistedFactory;
            import com.yandex.yatagan.Assisted;
            @AssistedFactory
            public interface FooFactory {
                Foo createFoo(@Assisted("c1") int count1, @Assisted("c2") int count2, String value);
            }
        """.trimIndent())
        givenJavaSource("test.Foo", """
            import com.yandex.yatagan.AssistedInject;
            import com.yandex.yatagan.Assisted;
            import javax.inject.Named;
            
            public class Foo { 
                public final Bar bar;
                @AssistedInject public Foo(
                    ScopedDep scopedDep,
                    UnscopedDep unscopedDep,
                    @Named("input") String string,
                    @Assisted("c2") int c2,
                    BarFactory factory,
                    @Assisted("c1") int c1,
                    @Assisted String v
                ) {
                    bar = factory.buildBar(c2, c1, v);
                }
            }
        """.trimIndent())
        givenJavaSource("test.Bar", """
            import com.yandex.yatagan.AssistedInject;
            import com.yandex.yatagan.Assisted;            

            public class Bar { 
                public final int c1;
                public final int c2;
                public final String v;
                @AssistedInject public Bar(
                    @Assisted("c1") int c1,
                    @Assisted("c2") int c2,
                    @Assisted String v
                ) {
                    this.c1 = c1;
                    this.c2 = c2;
                    this.v = v;
                }
            }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Module(subcomponents = [SubComponent::class])
            interface TestModule

            @Singleton
            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun fooFactory(): FooFactory
                @Component.Builder interface Factory {
                    fun create(
                        @BindsInstance @Named("input") string: String
                    ): TestComponent
                }
            }
            
            fun test() {
                val c: TestComponent = Yatagan.builder(TestComponent.Factory::class.java).create("foo")
                val f = c.fooFactory().createFoo(1, 2, "hello")
                assert(f.bar.c1 == 1)
                assert(f.bar.c2 == 2)
                assert(f.bar.v == "hello")
            }
        """.trimIndent())
        givenKotlinSource("test.SubComponent", """
            import com.yandex.yatagan.*
            @Component(isRoot = false)
            interface SubComponent {
                fun fooFactory(): FooFactory
                @Component.Builder interface Builder { fun create(): SubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }


    @Test
    fun `assisted inject in subcomponents`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @[Scope Retention(AnnotationRetention.RUNTIME)] annotation class RootScope

            @RootScope class ClassB @Inject constructor()
            @Singleton class ClassC @Inject constructor()
            @Singleton class ClassA @Inject constructor(
                val dep: Lazy<ClassB>, 
                val dep2: Lazy<ClassC>,
            )

            class Foo @AssistedInject constructor(
                val classA: Provider<ClassA>,
                val dep: Dep,
                val input: String,
                @Named("provision") val provision: String,        
                @Assisted val number: Int,
            )

            @AssistedFactory
            interface FooFactory {
                fun create(number: Int): Foo
            }

            @Module(subcomponents = [SubComponent::class])
            class RootModule {
                @Named("provision") @Provides fun provide(dep: Dep): String = "hello"                        
            }

            interface Dep {
                val input: String            
            }

            @RootScope            
            @Component(modules = [RootModule::class], dependencies = [Dep::class])
            interface RootComponent {
                val sub: SubComponent.Creator
                @Component.Builder interface Creator { fun create(dep: Dep): RootComponent }
            }

            @Singleton            
            @Component(isRoot = false)
            interface SubComponent {
                val factory: FooFactory            
                @Component.Builder
                interface Creator {
                    fun create(): SubComponent
                }
            }
    
            fun test() {
                val c: RootComponent.Creator = Yatagan.builder(RootComponent.Creator::class.java)
                val component = c.create(object : Dep { override val input get() = "" })    
                component.sub.create().factory.create(1)            
            }        
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `component inherits the same method from multiple interfaces`() {
        givenJavaSource("test.ClassA", """
            import javax.inject.Inject;
            public class ClassA { @Inject public ClassA() {} }
        """.trimIndent())
        givenJavaSource("test.MyDependencies0", """
            public interface MyDependencies0 {
                ClassA classA();
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*

            interface MyDependencies1 { fun classA(): ClassA }
            interface MyDependencies2 { fun classA(): ClassA }
            @Component interface MyComponent : MyDependencies0, MyDependencies1, MyDependencies2
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `multiple scopes`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Scope
            @Retention(AnnotationRetention.RUNTIME)
            annotation class Singleton2

            @[Singleton Singleton2]
            class ClassA @Inject constructor()
            
            @Component @[Singleton Singleton2]
            interface MyComponentA { val a: ClassA }
            
            @Component @[Singleton]
            interface MyComponentB { val a: ClassA }

            @Component @[Singleton2]
            interface MyComponentC { val a: ClassA }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `same-named field in class hierarchy with member-inject`() {
        givenJavaSource("test.ClassA", """
            public class ClassA<T> {
                @javax.inject.Inject public int mInt;
                @javax.inject.Inject public T mField2;
            }
        """.trimIndent())
        givenJavaSource("test.ClassB", """
            public class ClassB extends ClassA<Long> {
                @javax.inject.Inject public int mInt;
                @javax.inject.Inject public String mField2;
            }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            @Module class MyModule {
                @Provides fun provideInt() = 228
                @Provides fun provideString() = "hello"
            }
            @Component(modules = [MyModule::class]) interface MyComponent {
                fun injectInto(b: ClassB)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `@Binds alias is allowed to be duplicated`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class TestClass @Inject constructor()

            @Module interface MyModule {
                @Binds fun binds(i: TestClass): Any
                @Binds fun binds2(i: TestClass): Any
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                val obj: Any
                fun createSub(): SubComponent
            }

            @Component(isRoot = false, modules = [MyModule::class])
            interface SubComponent {
                val obj: Any
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}

