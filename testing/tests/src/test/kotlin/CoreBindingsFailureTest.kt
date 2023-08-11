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

import com.yandex.yatagan.processor.common.BooleanOption
import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class CoreBindingsFailureTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    private lateinit var features: SourceSet

    @Before
    fun setUp() {
        features = SourceSet {
            givenKotlinSource("test.features", """
                import com.yandex.yatagan.Condition                

                object Foo { 
                    val isEnabledA = true 
                    val isEnabledB = true 
                    val isEnabledC = true 
                }
                
                @Condition(Foo::class, "INSTANCE.isEnabledA") annotation class A
                @Condition(Foo::class, "INSTANCE.isEnabledB") annotation class B
                @Condition(Foo::class, "INSTANCE.isEnabledC") annotation class C
            """.trimIndent())
        }
    }

    @Test
    fun `rebind scope is forbidden`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            @Module interface TestModule {
                @Binds @Singleton fun number(i: Int): Number
                companion object {
                    @Provides fun integer(): Int = 0
                }
            }
            @Component(modules = [TestModule::class]) @Singleton interface TestComponent {
                val number: Number
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `missing dependency`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class Foo @Inject constructor(obj: Any, foo: Lazy<Foo2>)
            class Foo2 @Inject constructor(obj: Any)
            class ToInject {
                @set:Inject
                lateinit var obj: Any
            }
            
            @Component
            interface TestComponent {
                val foo: Foo
                val foo2: Foo2
                val hello: Any
                val bye: Any
                fun inject(i: ToInject)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `no compatible scope for inject`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module
            import javax.inject.Inject
            import javax.inject.Singleton
            import javax.inject.Scope
            
            @Scope
            annotation class ActivityScope
            
            @Singleton
            class Foo @Inject constructor()
            
            @Module(subcomponents = [SubComponent::class])
            interface RootModule

            @Component(modules = [RootModule::class])
            interface RootComponent {
                val fooForRoot: Foo
                val sub: SubComponent.Factory
            }
            
            @Component(isRoot = false)
            @ActivityScope
            interface SubComponent {
                val fooForSub: Foo
                @Component.Builder interface Factory { fun create(): SubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `no compatible scope for provision`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import javax.inject.Inject
            import javax.inject.Named
            import javax.inject.Singleton
            import javax.inject.Scope
            
            @Scope
            annotation class ActivityScope
            
            class Foo
            
            @Module(subcomponents = [SubComponent::class])
            class RootModule {
                @Singleton
                @Named("foo")
                @Provides fun provideFoo(i: Int): Foo = Foo()
            }
            
            @Component(modules = [RootModule::class])
            interface RootComponent {
                val sub: SubComponent.Factory
            }
            
            @Component(isRoot = false)
            @ActivityScope
            interface SubComponent {
                @get:Named("foo")
                val fooForSub: Foo
                @Component.Builder interface Factory { fun create(): SubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid bindings`() {
        givenJavaSource("test.JavaModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            @Module
            public interface JavaModule {
                @Provides default Number number() { return 0.0; }
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            @Module
            class TestModule {
                @Provides @IntoList 
                fun bindOne(): Int = 1
                @Provides @IntoList
                fun bindTwo(): Int = 2
                @Provides @IntoList(flatten = true)
                fun bindThreeForFive(): Int = 3
                @Provides fun hello() = Unit
                @Binds fun hello2() = Unit

                @Provides @IntoList @IntoSet
                fun bindThree(): Int = 3
            }
            @Module
            interface TestModule2 {
                @Provides fun provides(): Long
                @Provides fun provides2(): Long { return 99L }
                @Binds fun bindListToString(list: List<Int>): String
            }
            @Component(modules = [TestModule::class, TestModule2::class, JavaModule::class])
            interface TestComponent {
                fun getInts(): List<Int>
                fun number(): Number
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `condition propagates through alias and is validated`() {
        includeFromSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            annotation class NotAFeature

            interface ApiBase
            interface Api : ApiBase
            @Conditional(NotAFeature::class, A::class) class Impl @Inject constructor() : Api
            @Module interface TestModule {
                @Binds fun impl1(i: Impl): Api
                @Binds fun impl2(i: Api): ApiBase
                companion object {
                    @IntoList @Provides fun toAny1(i: Api): Any {
                        return i
                    }
                    @IntoList @Provides fun toAny2(i: ApiBase): Any {
                        return i
                    }
                }
            }
            @Component(modules = [TestModule::class]) interface TestComponent {
                val any: List<Any>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `incompatible conditions`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @AllConditions(
                Condition(Foo::class, "INSTANCE.isEnabledA"),
                Condition(Foo::class, "INSTANCE.isEnabledB"),
            )
            annotation class AandB
            
            @AnyCondition(
                Condition(Foo::class, "INSTANCE.isEnabledA"),
                Condition(Foo::class, "INSTANCE.isEnabledB"),
            )
            annotation class AorB
            
            @Conditional(A::class, B::class)
            class UnderAandB @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>)
            
            @Conditional(AorB::class, A::class)
            class UnderAorB @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>/*error*/)
            
            @Conditional(AandB::class, AorB::class)
            class UnderComplex @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>)
            
            @Conditional(A::class) class UnderA @Inject constructor(
                a: UnderAandB,  // error
                ab: UnderAorB,  // ok
            )
            @Conditional(AorB::class, B::class) class UnderB @Inject constructor(
                b: UnderA,  // error
                c: Lazy<UnderComplex>,  // error
            )
            
            @Module
            class MyModule {
                @Provides @Named("error")
                fun provideObject(c: UnderA): Any = c
                @Provides @Named("ok1")
                fun provideObject2(c: Optional<Provider<UnderA>>): Any = c
                @Provides(Conditional(A::class)) @Named("ok2")
                fun provideObject3(c: UnderA): Any = c
            }
            
            @Component(modules = [MyModule::class])
            interface MyComponent {
                val e1: Optional<UnderA>
                val e2: Optional<UnderB>
                @get:Named("error") val object1: Any
                @get:Named("ok1") val object2: Any
                @get:Named("ok2") val object3: Optional<Any>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `component features`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Heater
            
            @Conditional(A::class)
            class ElectricHeater @Inject constructor() : Heater
            
            @Conditional(B::class)
            class GasHeater @Inject constructor() : Heater
            
            class Stub : Heater
            
            @Qualifier private annotation class Private

            class Consumer {
                @set:Inject lateinit var heater: Heater
                @set:Inject lateinit var gasHeater: GasHeater
                @set:Inject lateinit var electricHeater: ElectricHeater
                @set:Inject lateinit var gasHeaterOptional: Optional<GasHeater>
                @set:Inject lateinit var electricHeaterOptional: Optional<ElectricHeater>
            }
            
            @Module(subcomponents = [SubComponentA::class, SubComponentB::class])
            interface RootModule {
                companion object {
                    @Provides @Private fun stubHeater() = Stub()
                }
            
                @Binds fun heater(e: ElectricHeater, g: GasHeater, @Private s: Stub): Heater
            }
            
            @Singleton
            @Component(modules = [RootModule::class])
            interface RootComponent {
                val heater: Heater  // ok
                val electric: ElectricHeater  // error
                val gas: GasHeater  // error
                
                fun injectConsumer(consumer: Consumer)
            }
            
            @Conditional(A::class)
            @Component(isRoot = false)
            interface SubComponentA {
                val electric: ElectricHeater  // ok
                val gas: GasHeater  // error
                
                @Component.Builder
                interface Creator { fun create(): SubComponentA }
            
                fun injectConsumer(consumer: Consumer)
            }
            
            @Conditional(B::class)
            @Component(isRoot = false)
            interface SubComponentB {
                val electric: ElectricHeater  // error
                val gas: GasHeater  // ok
                
                @Component.Builder
                interface Creator { fun create(): SubComponentB }
            
                fun injectConsumer(consumer: Consumer)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid features & variants`() {
        includeFromSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Condition(Foo::class, "!INSTANCE.isEnabledA")
            annotation class NotA
            
            annotation class NotAFeature
            annotation class NotAFeature2
            annotation class NotADimension
            @ComponentFlavor(dimension = NotADimension::class)
            annotation class InvalidFlavor
            annotation class NotAFlavor
            @ComponentFlavor(dimension = NotADimension::class)
            annotation class InvalidFlavor2

            @AnyCondition /*nothing here*/
            annotation class Never

            @AnyCondition(
                Condition(Foo::class, "INSTANCE.isEnabledA"),
                Condition(Foo::class, "!INSTANCE.isEnabledA"),
            )
            annotation class ComplexAlways
            
            @Conditional(NotAFeature::class, NotAFeature2::class,
                         onlyIn = [InvalidFlavor::class, NotAFlavor::class])
            class ClassA @Inject constructor()
            @Conditional(Never::class) class ClassB @Inject constructor()
            @Conditional(A::class, NotA::class) class ClassC @Inject constructor()
            @Conditional(ComplexAlways::class) class ClassD @Inject constructor()
            @Module(subcomponents = [AnotherComponent::class]) interface RootModule
            @Component(variant = [InvalidFlavor::class], modules = [RootModule::class])
            interface RootComponent {
                val a: Optional<ClassA>
                val b: Optional<ClassB>
                val c: Optional<ClassC>
                val d: Optional<ClassD>
            }
            @Component(variant = [InvalidFlavor::class, InvalidFlavor2::class, NotAFlavor::class], isRoot = false)
            interface AnotherComponent { @Component.Builder interface C { fun c(): AnotherComponent } }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid conditions`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            object Foo {
                val foo: String = ""
            }

            @Condition(Foo::class, "!hello")
            annotation class Invalid1
            @Condition(Int::class, "#invalid")
            annotation class Invalid2
            @Condition(Foo::class, "INSTANCE.getFoo")
            annotation class Invalid3
            @Condition(Foo::class, "getFoo")
            annotation class Invalid4

            @Conditional(
                Invalid1::class,
                Invalid2::class,
                Invalid3::class,
                Invalid4::class,
            )
            class ClassA @Inject constructor()
            
            @Component
            interface MyComponent {
                val a: Optional<ClassA>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `inconsistent non-static bindings`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class FeatureProvider(
                val isEnabledA: Boolean,
                val isEnabledC: Boolean,
            ) {
                @Condition(FeatureProvider::class, "isEnabledA") annotation class A
                @Condition(FeatureProvider::class, "isEnabledC") annotation class C
            }

            @Conditional(FeatureProvider.A::class /* error incompatible condition-under-condition */)
            class FeatureProvider2 @Inject constructor(c: Optional<ClassC> /* error: loop */) : Conditions {
                override val isEnabledD: Boolean get() = true
            }
            
            @Conditional(FeatureProvider.A::class)
            class ClassA @Inject constructor()

            @Conditional(FeatureProvider.C::class, Conditions.D::class)
            class ClassC @Inject constructor()

            interface Conditions {
                val isEnabledD: Boolean
                
                @Condition(Conditions::class, "isEnabledD") annotation class D
            }

            @Module
            interface TestModule {
                @Binds fun conditions(i: FeatureProvider2): Conditions
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                val a: Optional<ClassA>
                val c: Optional<ClassC>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `conflicting bindings`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Qualifier
            annotation class MyQualifier(val named: Named)
            @Qualifier
            annotation class Numbered(val value: Int)
            const val Hello = "hello"
            
            interface Dependency {
                val myString: String
            }
            
            interface Api
            class Impl1 @Inject constructor(): Api
            class Impl2 @Inject constructor(): Api
            
            @Module
            class MyModule {
                @MyQualifier(Named("hello"))
                @Provides fun provideObject(): Any { return Any() }
                
                @MyQualifier(Named(value = Hello))
                @Provides fun provideObject2(): Any { return Any() }
            
                @Provides fun c1() : MyComponent1 = throw AssertionError()
                @Named("flag") @Provides fun bool() = true
                @Provides fun dep(): Dependency = throw AssertionError()
                @Provides fun provideApi(): Api = throw AssertionError()
            }
            
            @Module interface MyBindsModule {
                @Binds fun api1(i: Impl1): Api
                @Binds fun api2(i: Impl2): Api
            }
            
            @Module(includes = [MyModule::class, MyBindsModule::class], subcomponents = [SubComponent::class])
            class MyModule2 {
                @Provides @IntoList fun one(): Number = 1L
                @Provides @IntoList fun three(): Number = 3f
                @Provides @IntoList fun two(): Number = 2.0
            
                @Provides fun numbers(): List<Number> = emptyList()
                @Provides fun builder(): SubComponent.Builder = throw AssertionError()
            }
            @Module
            object MySubModule {
                @Provides fun numbers2(): List<Number> = emptyList()
            }
            
            interface Base {
                @get:MyQualifier(Named("hello")) val any: Any
                @get:Named("flag") val flag: Boolean
                val c: MyComponent1
                val dep: Dependency
            }
            @Component(modules = [MyModule::class])
            interface MyComponent1 : Base
            @Component(modules = [MyModule2::class], dependencies = [Dependency::class])
            interface MyComponent2 : Base {
                val numbers: List<Number>
                val sub: SubComponent.Builder
                val string: String
                val api: Api

                @Component.Builder
                interface Builder {
                    @BindsInstance fun setFlag(@Named("flag") i: Boolean)
                    @BindsInstance fun setNumbers(i: MutableList<Number>)
                    fun withDependency(i: Dependency)
                    @BindsInstance fun withString(i: String)
                    @BindsInstance fun withAnotherString(i: String)
                    fun create(): MyComponent2
                }
            }
            @Component(isRoot = false, modules = [MySubModule::class])
            interface SubComponent {
                val numbers: List<Number>
                @Component.Builder interface Builder { fun create(): SubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `conflicting aliases with an option`() {
        givenOption(BooleanOption.ReportDuplicateAliasesAsErrors, true)
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            interface Api
            class Impl1 @Inject constructor(): Api
            class Impl2 @Inject constructor(): Api

            @Module interface MyModule {
              @Binds fun bind1(i: Impl1): Api
              @Binds fun bind2(i: Impl2): Api
            }

            @Component(modules = [MyModule::class])
            interface MyComponent {
              val api: Api
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `manual framework type usage`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class WithInject @Inject constructor()

            @Module
            class MyModule {
                @Provides fun illegalOptional(): Optional<Any> = Optional.empty()
                @Provides fun illegalLazy(): Lazy<Int> = throw AssertionError()
                @Provides fun illegalProvider(): Provider<String> = throw AssertionError()
            }

            @Component(modules = [MyModule::class])
            interface RootComponent {
                val o1: Optional<Optional<WithInject>>
                val o2: Provider<Optional<WithInject>>
                val o3: Optional<Lazy<Optional<WithInject>>>
                
                @Component.Builder
                interface Builder {
                    fun create(
                        @BindsInstance flagProvider: Provider<Boolean>,
                        @BindsInstance optionalFloat: Optional<Float>,
                    ): RootComponent
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `multi-thread status mismatch`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            
            @Module(subcomponents = [SubComponent1::class]) interface RootModule
            @Component(modules = [RootModule::class]) interface RootComponent
            
            @Component(multiThreadAccess = true, isRoot = false)
            interface SubComponent1 {
                @Component.Builder interface B { fun c(): SubComponent1 } 
            }
            
            @Component(isRoot = false)
            interface SubComponent2 { 
                @Component.Builder interface B { fun c(): SubComponent2 }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `unresolved complex annotation as qualifier`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

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

            @Component
            interface TestComponent {
                @get:ComplexQualifier(
                    228,
                    number = -22,
                    name = "hello" + " world",
                    arrayString = ["hello", "world"],
                    arrayInt = [1,2,3],
                    nested = Named("nested-named"),
                    arrayChar = ['A', 'B', 'C'],
                    arrayNested = [Named("array-nested")],
                    enumValue = MyEnum.Red,
                )
                val any: Any
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid assisted inject`() {
        givenJavaSource("test.ClassI", """
            import javax.inject.Inject;

            public class ClassI {
                @Inject public ClassI() {}
            }
        """.trimIndent())
        givenJavaSource("test.FactoryA", """
            import com.yandex.yatagan.Assisted;
            import com.yandex.yatagan.AssistedFactory;

            @AssistedFactory
            public abstract class FactoryA {
                abstract void someMethod(@Assisted("id") int a, @Assisted("id") int b);
            }
        """.trimIndent())
        givenJavaSource("test.FactoryB", """
            @com.yandex.yatagan.AssistedFactory
            public interface FactoryB {}
        """.trimIndent())
        givenJavaSource("test.ClassA", """
            import com.yandex.yatagan.AssistedInject;
            import com.yandex.yatagan.Assisted;

            public class ClassA {
                @AssistedInject public ClassA(@Assisted("A") int a, ClassI i, @Assisted("A") int b) {}
            }
        """.trimIndent())
        givenJavaSource("test.FactoryC", """
            import com.yandex.yatagan.Assisted;
            import com.yandex.yatagan.AssistedFactory;
            @AssistedFactory
            public interface FactoryC {
                ClassA createA(@Assisted("A") int a, @Assisted("B") int b);
            }
        """.trimIndent())

        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.*
            @Component
            interface TestComponent {
                fun a(): FactoryA
                fun b(): FactoryB
                fun c(): FactoryC
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid map binding`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            import kotlin.reflect.KClass

            @IntoMap.Key annotation class InvalidMapKey1
            @IntoMap.Key annotation class InvalidMapKey2(val value: InvalidMapKey1)
            @IntoMap.Key annotation class InvalidMapKey3(val value: IntArray)
            @IntoMap.Key annotation class ClassKey2(val value: KClass<*>)

            @Module(subcomponents = [SubComponent::class])
            interface TestModule {
                @[Binds IntoMap]
                fun binding1(): Int
                
                @[Binds IntoMap InvalidMapKey1]
                fun binding2(): Int
                
                @[Binds IntoMap InvalidMapKey2(InvalidMapKey1())]
                fun binding3(): Int
                
                @[Binds IntoMap InvalidMapKey3([1, 2, 3])]
                fun binding4(): Int
                
                @Binds @IntoMap
                @ClassKey2(Any::class)
                @ClassKey(String::class)
                fun binding5(): String
                
                @[Binds IntoMap ClassKey(Any::class)]
                fun binding6(): String
            }

            @Module class SubModule {
                @[Provides IntoMap ClassKey(Any::class)]
                fun subBinding(): String = "hi"
            } 

            @Component(modules = [TestModule::class])
            interface TestComponent {
                val sub: SubComponent.Builder
                val map: Map<Class<*>, String>
            }

            @Component(isRoot = false, modules = [SubModule::class])
            interface SubComponent {
                val map: Map<Class<*>, String>
                
                @Component.Builder
                interface Builder { fun create(): SubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid multibinding declaration`() {
        givenJavaSource("test.JavaModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Multibinds;
            import java.util.List;
            import java.util.Map;

            @Module
            interface JavaModule {
                @Multibinds
                List list();
                
                @Multibinds
                Map map();
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Module class MyModule {
                @Multibinds fun mapDeclaration(): Map<Int, String> = emptyMap()
                @Multibinds fun listDeclaration(): List<Int> = emptyList()
            }
            @Module interface MyModule2 {
                @Multibinds fun mapDeclaration1(arg: Int): Map<Int, String>
                @Multibinds fun listDeclaration1(arg: Int): List<Int>
                
                @Multibinds fun mapDeclaration2(arg: Int): Map<*, *>
                @Multibinds fun listDeclaration2(arg: Int): List<*>
                
                @Multibinds fun <T> mapDeclaration3(arg: Int): Map<T, *>
                @Multibinds fun <T> listDeclaration3(arg: Int): List<T>
            }

            @Component(modules = [
                MyModule::class,
                MyModule2::class,
                JavaModule::class,
            ])
            interface MyComponent
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid reusable scope usage`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Reusable @Singleton 
            class MyClass @Inject constructor()

            @Reusable @Component 
            interface MyComponent {
                val c: MyClass
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}