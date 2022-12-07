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
class CoreBindingsKotlinTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `basic component - @Module object`() {
        givenKotlinSource("test.Api", """interface Api""")
        givenKotlinSource("test.Impl", """class Impl : Api""")
        givenKotlinSource("test.ExplicitImpl", """class ExplicitImpl : Api""")

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.yatagan.Provides
        import com.yandex.yatagan.Module

        @Module
        object MyModule {
          @Provides
          fun provides(): Api = Impl()
          
          @Provides
          @JvmStatic
          fun providesExplicit() = ExplicitImpl()
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import javax.inject.Provider

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                fun getExplicit(): test.ExplicitImpl
                fun getProviderExplicit(): Provider<test.ExplicitImpl>
                fun getLazyExplicit(): Lazy<test.ExplicitImpl>
            }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - nested component class`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*

            class TopLevelClass {
                class NestedClass {
                    @Component interface Component1
                }
                @Component interface Component2 {
                    @Component.Builder interface Builder { fun c(): Component2 }
                }
            }

            fun test() {
                Yatagan.create(TopLevelClass.NestedClass.Component1::class.java)
                Yatagan.builder(TopLevelClass.Component2.Builder::class.java)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - @Module with companion object`() {
        givenKotlinSource("test.Api", """interface Api""")
        givenKotlinSource("test.Impl", """class Impl : Api""")
        givenKotlinSource("test.ExplicitImpl", """class ExplicitImpl""")

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.yatagan.Provides
        import com.yandex.yatagan.Module

        @Module
        interface MyModule {
            companion object {
                @Provides
                fun provides(): Api = Impl()

                @Provides
                @JvmStatic
                fun providesExplicit() = ExplicitImpl()
            }
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import javax.inject.Provider

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                fun getExplicit(): test.ExplicitImpl
                fun getProviderExplicit(): Provider<test.ExplicitImpl>
                fun getLazyExplicit(): Lazy<test.ExplicitImpl>
            }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - @Provides with dependencies in companion`() {
        givenKotlinSource("test.Api", """interface Api""")
        givenKotlinSource("test.Impl", """class Impl : Api""")

        givenKotlinSource("test.BaseModule", """
            import com.yandex.yatagan.Provides

            open class BaseModule {
                @Provides
                fun get(): Api = Impl()
            } 
        """.trimIndent()
        )

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.yatagan.Module

        @Module
        interface MyModule {
            companion object : BaseModule()
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import javax.inject.Provider
            import javax.inject.Singleton

            @Component(modules = [MyModule::class]) @Singleton
            interface TestComponent {
                fun get(): Api
                fun getProvider(): Provider<Api>
                fun getLazy(): Lazy<Api>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - @Module instance`() {
        givenKotlinSource("test.Api", """interface Api { val id: Int }""")

        givenKotlinSource(
            "test.MyModule", """
        import com.yandex.yatagan.*

        @Module
        class MyModule(private val myId: Int) {
          @Provides
          fun provides(): Api = object : Api { override val id get() = myId }
        }
        """.trimIndent()
        )
        givenKotlinSource(
            "test.TestComponent", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): test.Api
                fun getProvider(): Provider<test.Api>
                fun getLazy(): Lazy<test.Api>
                
                @Component.Builder
                interface Factory {
                    fun create(module: MyModule): TestComponent
                }
            }
        """.trimIndent()
        )

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            fun test() {
                val m = MyModule(52)
                val c = Yatagan.builder(TestComponent.Factory::class.java).create(m)
                assert(c.get().id == 52)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - properties as entry-points`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import javax.inject.Provider
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module

            interface Api
            class Impl : Api

            @Module
            object MyModule {
              @Provides
              @JvmStatic
              fun api(): Api = Impl()
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                val api: test.Api
                val provider: Provider<test.Api>
                val lazy: Lazy<test.Api>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - @Provides property`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import javax.inject.Provider
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module

            interface Api
            class Impl : Api

            @Module
            object MyModule {
                @get:Provides
                val api: Api = Impl()
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun api(): test.Api
                fun provider(): Provider<test.Api>
                fun lazy(): Lazy<test.Api>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component with factory - BindsInstance`() {
        givenJavaSource("test.MyClass", """
        public class MyClass {}
        """.trimIndent())
        givenKotlinSource("test.TestComponent", """
        import javax.inject.Provider
        import com.yandex.yatagan.Component
        import com.yandex.yatagan.BindsInstance
        import com.yandex.yatagan.Lazy

        @Component
        interface TestComponent {
            fun get(): MyClass
            val provider: Provider<MyClass>
            val lazy: Lazy<MyClass>
            
            @Component.Builder
            interface Factory {
               fun create(@BindsInstance instance: MyClass): TestComponent 
            }
        }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide Any`() {
        givenKotlinSource("test.MyModule", """
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module

            @Module
            object MyModule {
                @Provides
                fun provides(): Any {
                    return "object"
                }
            }
        """)

        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.Component

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): Any
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - consume Lazy (wildcard type test)`() {
        givenKotlinSource("test.MyModule", """
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import javax.inject.Inject

            class ClassA @Inject constructor(f: Lazy<ClassB>)
            class ClassB @Inject constructor()
            class ClassC

            @Module
            object MyModule {
                @Provides fun classC() = ClassC() 
                @Provides fun classB(f: Lazy<ClassC>): Any = f.get()
            }
        """.trimIndent())

        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.Component

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): ClassA
                fun b(): Any
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide list of strings`() {
        givenKotlinSource("test.MyModule", """
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module

            @Module
            object MyModule {
                @Provides
                fun provides(): List<String> = listOf()
            }
        """
        )

        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.Component

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): List<String>
            }
        """)

        compileRunAndValidate()
    }

    @Test
    fun `basic component - provide array type`() {
        givenKotlinSource("test.TestComponent", """
            import javax.inject.Inject
            import javax.inject.Provider
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Module
            
            @Module
            object MyModule {
                @Provides
                fun providesIntArray(): IntArray {
                    return intArrayOf(228)
                }
                
                @Provides
                fun providesDoubleArray(): Array<Double> {
                    return arrayOf(7.40)
                }
                
                @Provides
                fun providesStringArray(): Array<String> {
                    return arrayOf("foo", "bar")
                }
            }
            
            class Consumer<T> @Inject constructor (
                 i1: IntArray, i2: Provider<IntArray>, i3: Array<T>, i4: Provider<Array<T>>, 
                 i5: Array<String>, i6: Provider<Array<String>>,
            )
            
            @Component(modules = [MyModule::class])
            interface TestComponent {
                val int2: IntArray
                val intLazy: Lazy<IntArray>
                val intProvider: Provider<IntArray>
            
                val doubleProvider: Provider<Array<Double>>
                val double2: Array<Double>
                
                val stringProvider: Provider<Array<String>>
                val string2: Array<String>
                
                val c: Consumer<Double>
            }
            """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic component - universal builder support + variance test`() {
        givenJavaSource("test.Wrapper", """
            public class Wrapper<T> { public Wrapper(T dep) {} }
        """.trimIndent())
        givenJavaSource("test.SomeOpenClass", """
            public class SomeOpenClass {}
        """.trimIndent())
        givenJavaSource("test.Dependency", """
            import com.yandex.yatagan.Lazy;
            public interface Dependency {
                Wrapper<Lazy<SomeOpenClass>> getWrapper();
            }
        """.trimIndent())
        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.*
            import javax.inject.*

            const val BAR = "bar"

            class MyClass @Inject constructor (
                    @Named("foo") o1: Object,
                    @Named(BAR) o2: Object,
                    @Named("baz") o3: Object,
                    foos: Set<Foo>,
                    bazs: Set<Baz>,
                    bars: MutableSet<Bar>,
                    fooConsumer: Consumer<Foo>,
                    bazConsumer: Consumer<Baz>,
                    w: Wrapper<Lazy<SomeOpenClass>>,
            )
            interface Foo
            interface Bar
            class Baz

            interface Consumer<in T>

            interface CreatorBase {
                @BindsInstance
                fun setFoo(@Named("foo") obj: Any): CreatorBase
            }

            @Component(dependencies = [Dependency::class])
            interface TestComponent {
                fun get(): MyClass
                val foos: MutableSet<out Foo>
                val bars: Set<out Bar>
                val bars2: Set<Bar>
                val bazs: Set<Baz>
                
                @Component.Builder
                interface Creator : CreatorBase {
                    @BindsInstance
                    fun setBar(@Named("bar") obj: Any): Creator
                    
                    @BindsInstance
                    fun setSetOfFoo(foos: Set<Foo>)
                    
                    @BindsInstance
                    fun setBazs(bazs: Set<Baz>)
                    
                    @BindsInstance
                    fun setSetOfBar(bars: MutableSet<Bar>): Creator
                    
                    fun create(
                        dep: Dependency,
                        @BindsInstance fooConsumer: Consumer<Foo>,
                        @BindsInstance bazConsumer: Consumer<Baz>,
                        @BindsInstance @Named("baz") obj: Any,
                    ): TestComponent
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `basic members inject`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Api
            @Singleton class ClassA @Inject constructor() : Api
            @Singleton class ClassB @Inject constructor(): Api
            
            @Module
            interface MyModule {
                @Named("hello") @Binds fun classAHello(i: ClassA): Api
                @Named("bye") @Binds fun classBBye(i: ClassB): Api
            }
            
            class Foo {
                @set:Inject @set:Named("hello")
                lateinit var helloA: Provider<Api>
                
                @Inject @field:Named("bye")  // Inject into field
                lateinit var bye: Provider<Api>
                
                @set:Inject @set:Named("bye")  // Inject via setter
                lateinit var bye2: Provider<Api>
                
                lateinit var b: ClassB
                    private set
                private lateinit var _a: ClassA
                
                @Inject
                fun setB(i: ClassB) { b = i }
                
                var a: ClassA 
                    get() = _a 
                    @Inject
                    set(value) { _a = value }
            }
            
            @Singleton
            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun injectFoo(foo: Foo)
            }
            
            fun test() {
                val foo = Foo()
                Yatagan.create(TestComponent::class.java).injectFoo(foo)
                foo.helloA; foo.bye; foo.b; foo.a
            }""".trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `advanced member inject`() {
        givenJavaSource("test.JavaQualifier", """
            import java.lang.annotation.ElementType;

            @javax.inject.Qualifier
            @java.lang.annotation.Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface JavaQualifier {}
        """.trimIndent())
        givenJavaSource("test.JavaBase", """
            import javax.inject.Provider;
            import javax.inject.Inject;
            public class JavaBase<T, K> {
                @Inject public Provider<T> javaBaseField;
                @Inject public K javaBaseField2;
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import javax.inject.*
            import com.yandex.yatagan.*
    
            @Qualifier
            @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
            annotation class KotlinQualifierForField

            @Qualifier
            @Target(AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
            annotation class KotlinQualifierForSetter

            interface ApiKotlin {
                @set:Inject @set:JavaQualifier  // This should be ignored
                var apiProp: Any
            }

            open class Base : JavaBase<Int, Int>(), ApiKotlin {
                @Inject @JavaQualifier  // Expected to apply to field
                lateinit var baseLateInitProp: Any
                
                @Inject @KotlinQualifierForField
                lateinit var baseLateInitPropK: Any
    
                @Inject @JavaQualifier
                override lateinit var apiProp: Any
            }

            class Derived : Base() {
                @set:Inject @set:JavaQualifier
                lateinit var lateInitProp: Any
                
                @set:Inject @set:KotlinQualifierForSetter
                lateinit var lateInitPropK: Any
            }

            @Module
            class MyModule {
                var javaQualifiedCount = 0
                var kotlinForSetterQualifiedCount = 0
                var kotlinForFieldQualifiedCount = 0

                @Provides @JavaQualifier fun a() = Any().also { ++javaQualifiedCount }
                @Provides @KotlinQualifierForField fun b() = Any().also { ++kotlinForFieldQualifiedCount }
                @Provides @KotlinQualifierForSetter fun c() = Any().also { ++kotlinForSetterQualifiedCount }
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun inject(d: Derived)
                
                @Component.Builder
                interface Creator {
                    fun create(
                        module: MyModule,
                        @BindsInstance arg: Int,
                    ): TestComponent
                }
            }

            fun test() {
                val c: TestComponent.Creator = Yatagan.builder(TestComponent.Creator::class.java)
                val module = MyModule()
                val component = c.create(module = module, arg = 1)
                    
                val d = Derived()
                component.inject(d)
                checkNotNull(d.lateInitProp)
                checkNotNull(d.baseLateInitProp)
                checkNotNull(d.baseLateInitPropK)
                checkNotNull(d.lateInitPropK)
                checkNotNull(d.javaBaseField)
                checkNotNull(d.javaBaseField2)
                checkNotNull(d.apiProp)
                assert(module.javaQualifiedCount == 3)
                assert(module.kotlinForSetterQualifiedCount == 1)
                assert(module.kotlinForFieldQualifiedCount == 1)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `trivially constructable module`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            
            @Module
            class MyModule {
                private val mObj = Any()
                @Provides
                fun provides(): Any {
                    return mObj
                }
            }
            
            @Component(modules = [MyModule::class])
            interface MyComponent {
                fun get(): Any
            }
            
            @Component(modules = [MyModule::class])
            interface MyComponent2 {
                fun get(): Any
            
                @Component.Builder
                interface Factory {
                    fun build(): MyComponent2
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `type variables in inject constructor`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Binds
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Optional
            import javax.inject.Inject
            import javax.inject.Provider
            
            class DependencyA @Inject constructor()
            class DependencyB<T> @Inject constructor(i: T)
            class SomeClass<T> @Inject constructor(i: T)
            
            class Consumer @Inject constructor(a: Lazy<SomeClass<DependencyA>>)
            
            interface Absent<T> {
                @Binds fun foo(): T
            }
            
            @Module
            interface MyModule : Absent<Any>
            
            @Component(modules = [MyModule::class])
            interface MyComponent {
                val any: Optional<Any>
                val clazz: SomeClass<DependencyA>
                val provider: Provider<SomeClass<DependencyB<DependencyA>>>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `complex annotation as qualifier`() {
        givenPrecompiledModule(SourceSet {
            givenKotlinSource("mod.Constants", """
                const val CONSTANT = "hello-from-const-val"
            """.trimIndent())
        })
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            import mod.CONSTANT

            enum class MyEnum {
                Red, Green, Blue,
            }

            @Qualifier
            @Retention(AnnotationRetention.RUNTIME)
            annotation class ComplexQualifier(
                val value: Int,
                val number: Long,
                val name: String,
                val arrayString: Array<String>,
                val arrayInt: IntArray,
                val arrayChar: CharArray = ['A', 'B', 'C'],
                val nested: Named,
                val arrayNested: Array<Named>,
                val enumValue: MyEnum,
            )

            class ClassA @Inject constructor(
                @ComplexQualifier(
                    228,
                    number = -22,
                    name = "hello" + " world",
                    arrayString = ["hello", "world", CONSTANT],
                    arrayInt = [1,2,3],
                    nested = Named("nested-named"),
                    arrayChar = ['A', 'B', 'C'],
                    arrayNested = [Named("array-nested")],
                    enumValue = MyEnum.Red,
                )
                val errorDependency: Any
            )

            @Module
            class MyModule {
                @get:Provides @get:ComplexQualifier(
                    value = 200 + 28,
                    name = "hello" + " world",
                    number = -11 - 11,
                    arrayString = ["hel" + "lo", "world", "hello-from-const-val"],
                    arrayInt = [1,2, 6 - 3],
                    nested = Named("" + "nested-named"),
                    arrayChar = ['A', 'B', 'C'],
                    arrayNested = [Named("array-nested")],
                    enumValue = MyEnum.Red,
                )
                val any: Any = Any()
            }
            @Component(modules = [MyModule::class])
            interface TestComponent {
                val a: ClassA
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `complex annotation as qualifier in java`() {
        givenPrecompiledModule(SourceSet {
            givenKotlinSource("mod.Constants", """
                const val CONSTANT = "hello-from-const-val"
            """.trimIndent())
        })
        givenJavaSource("test.MyEnum", """
            enum MyEnum {
                Red, Green, Blue,
            }
        """.trimIndent())
        givenJavaSource("test.ComplexQualifier", """
            import javax.inject.Named;
            import javax.inject.Qualifier;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @Qualifier
            @Retention(RetentionPolicy.RUNTIME)
            @interface ComplexQualifier {
                int value();
                long number();
                String name();
                String[] arrayString();
                int[] arrayInt();
                char[] arrayChar() default {'A', 'B', 'C'};
                Named nested();
                Named[] arrayNested();
                MyEnum enumValue();
            }
        """.trimIndent())
        givenJavaSource("test.ClassA", """
            import javax.inject.Named;
            import javax.inject.Inject;
            import mod.ConstantsKt;
            public class ClassA {
                @Inject public ClassA(
                    @ComplexQualifier(
                        value = 228,
                        number = -22,
                        name = "hello" + " world",
                        arrayString = {"hello", "world", ConstantsKt.CONSTANT},
                        arrayInt = {1,2,3},
                        nested = @Named("nested-named"),
                        arrayChar = {'A', 'B', 'C'},
                        arrayNested = {@Named("array-nested")},
                        enumValue = MyEnum.Red
                    )
                    Object errorDependency
                ) {}
            }
        """.trimIndent())
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Component;
            import com.yandex.yatagan.Provides;
            import javax.inject.Named;

            @Module
            public interface MyModule {
                static @Provides @ComplexQualifier(
                    value = 200 + 28,
                    name = "hello" + " world",
                    number = -11 - 11,
                    arrayString = {"hel" + "lo", "world", "hello-from-const-val"},
                    arrayInt = {1,2, 6 - 3},
                    nested = @Named("" + "nested-named"),
                    arrayChar = {'A', 'B', 'C'},
                    arrayNested = {@Named("array-nested")},
                    enumValue = MyEnum.Red
                )
                Object any() { return new Object(); }
            }
            @Component(modules = {MyModule.class})
            interface TestComponent {
                ClassA getA();
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}

