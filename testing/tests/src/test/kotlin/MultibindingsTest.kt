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

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class MultibindingsTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `basic test`() {
        givenJavaSource("test.Create", """
            public interface Create {}
        """.trimIndent())
        givenJavaSource("test.ConsumerJava", """
            import javax.inject.Inject;
            import javax.inject.Provider;
            import java.util.List;
            
            public class ConsumerJava {
                public @Inject ConsumerJava(List<Create> i1, Provider<List<Create>> i2) {
                }
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Singleton class ClassA @Inject constructor (b: ClassB) : Create
            @Singleton class ClassB @Inject constructor() : Create
            @Singleton class ClassC @Inject constructor (a: ClassA) : Create
            class Consumer @Inject constructor(list: List<Create>, later: Provider<List<Create>>)
            
            @Module
            interface MyRedundantModule {
                @IntoList @Binds fun createClassA(a: ClassA): Create
                @IntoList @Binds fun createClassA2(a: ClassA): Create
            }

            @Module(includes = [MyRedundantModule::class])
            interface MyModule {
                // Duplicate binds should not introduce duplicates into the list
                @IntoList @Binds fun createClassA(a: ClassA): Create
                @IntoList @Binds fun createClassA2(a: ClassA): Create
                
                @IntoList @Binds fun createClassB(b: ClassB): Create
                @IntoList @Binds fun createClassB2(b: ClassB): Create
                @IntoList @Binds fun createClassC3(c: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
                fun bootstrapLater(): Provider<List<Create>>
                val c: Consumer
                val c2: ConsumerJava
            }
            
            fun test() {
                val c: TestComponent = Yatagan.create(TestComponent::class.java)
                
                val bootstrapList = c.bootstrap()
                assert(bootstrapList !== c.bootstrap())
                assert(bootstrapList.size == 3)
                assert(bootstrapList[0] is ClassB) {"classB"}
                assert(bootstrapList[1] is ClassA) {"classA"}
                assert(bootstrapList[2] is ClassC) {"classC"}
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `default binding ordering for list bindings`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Create
            interface Destroy
            interface ActivityDestroy: Destroy
            
            class CreateA @Inject constructor() : Create
            @Singleton class CreateB @Inject constructor(a: CreateA) : Create
            
            @Singleton open class CreateDestroyA @Inject constructor() : Create, Destroy
            @Singleton class CreateDestroyB @Inject constructor() : Create, ActivityDestroy
            @Singleton open class CreateDestroyC @Inject constructor() : CreateDestroyA()
            @Singleton class CreateDestroyD : CreateDestroyC()
            
            @Singleton class DestroyA @Inject constructor() : ActivityDestroy

            class CreateX : Create
            class CreateZ @Inject constructor() : Create

            interface MyDependency : Create {
                @get:Named("from-depA")
                val myNewCreate2: Create
                @get:Named("from-depB")
                val myNewCreate1: Create
            }

            class MyDependencyImpl : MyDependency {
                override val myNewCreate1: Create
                    get() = CreateA()
                override val myNewCreate2: Create
                    get() = CreateZ()
            }
            
            @Module
            interface MyModule {                
                @Multibinds fun create(): List<Create>
                @Binds @IntoList fun create(i: CreateA): Create
                @Binds @IntoList fun create(i: CreateB): Create
                @Binds @IntoList fun createDependency(i: MyDependency): Create
                @Binds @IntoList fun createComponent(i: MyComponent): Create
                @Binds @IntoList fun create(i: CreateDestroyB): Create
                @Binds @IntoList fun create(i: CreateDestroyA): Create
                @Binds @IntoList fun create(i: CreateX): Create
                @Binds @IntoList fun create(i: CreateDestroyD): Create
                @Binds @IntoList fun create(i: CreateDestroyC): Create
                @Binds @IntoList fun createA(@Named("from-depA") i: Create): Create
                @Binds @IntoList fun createB(@Named("from-depB") i: Create): Create
                
                @Multibinds fun destroy(): List<Destroy>
                @Binds @IntoList fun destroy(i: CreateDestroyA): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyB): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyD): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyC): Destroy
                @Binds @IntoList fun activityDestroy(i: DestroyA): Destroy
                
                @Binds @IntoList fun allCreates(i: List<Create>): List<@JvmWildcard Any>
                @Binds @IntoList fun numbers(i: List<Number>): List<@JvmWildcard Any>
                @Binds @IntoList fun allDestroys(i: List<Destroy>): List<@JvmWildcard Any>
                
                companion object {
                    @Provides fun createDestroyD() = CreateDestroyD()
                    @Provides fun myListOfNumbers(): List<@JvmWildcard Number> = listOf(1, 2f, 3.0)
                }
            }
            
            @Singleton
            @Component(modules = [MyModule::class], dependencies = [MyDependency::class])
            interface MyComponent : Create {
                val bootstrapCreate: List<Create>
                val bootstrapDestroy: List<Destroy>
                val allLists: List<List<@JvmWildcard Any>>
                
                @Component.Builder
                interface Builder {
                    fun create(
                        @BindsInstance x: CreateX,
                        dep: MyDependency,
                    ): MyComponent
                }
            }
            
            fun test() {
                val builder: MyComponent.Builder = Yatagan.builder(MyComponent.Builder::class.java)
                val c = builder.create(CreateX(), MyDependencyImpl())
                       
                val create = c.bootstrapCreate
                val destroy = c.bootstrapDestroy
                val allLists = c.allLists
            
                var i = 0
                assert(create[i++] is CreateDestroyD)
                assert(create[i++] is CreateA)
                assert(create[i++] is CreateB)
                assert(create[i++] is CreateDestroyA)
                assert(create[i++] is CreateDestroyB)
                assert(create[i++] is CreateDestroyC)
                assert(create[i++] is MyDependency)
                assert(create[i++] is CreateA)  // from myNewCreate1
                assert(create[i++] is CreateZ)  // from myNewCreate2
                assert(create[i++] is MyComponent)
                assert(create[i++] is CreateX)
                assert(create.size == i)

                assert(destroy.size == 5)
                assert(destroy.map { it::class } == listOf(CreateDestroyD::class, 
                    CreateDestroyA::class, CreateDestroyB::class, 
                    CreateDestroyC::class, DestroyA::class))

                assert(allLists[0].size == 3)  // numbers
                assert(allLists[1].size == destroy.size)
                assert(allLists[2].size == create.size)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `list declaration binds empty list`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Create
            
            @Module
            interface MyModule {
                @Multibinds fun create(): List<Create>
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }
            
            fun test() {
                assert(Yatagan.create(TestComponent::class.java).bootstrap().isEmpty())
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `multi-bound list with conditional entries`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class Features {
                companion object {
                    var isEnabled = false
                }
            
                @Condition(Features::class, "Companion.isEnabled")
                annotation class Feature
            }
            
            interface Create
            
            @Singleton
            class ClassA @Inject constructor (b: Optional<ClassB>) : Create
            
            @Singleton
            @Conditional(Features.Feature::class)
            class ClassB @Inject constructor() : Create
            
            @Singleton
            class ClassC @Inject constructor (c: ClassA) : Create
            
            @Module
            interface MyModule {
                @Multibinds fun create(): List<Create>
                @Binds @IntoList fun create(i: ClassA): Create
                @Binds @IntoList fun create(i: ClassB): Create
                @Binds @IntoList fun create(i: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }

            fun test() {
                val c: TestComponent = Yatagan.create(TestComponent::class.java)
                assert(c.bootstrap().map { it::class } == listOf(ClassA::class, ClassC::class)) 
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `flattening contribution`() {
        givenJavaSource("test.TestModule", """
            import com.yandex.yatagan.IntoList;
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            import java.util.Set;
            import java.util.List;
            import java.util.Collection;

            @Module
            public class TestModule {
                @Provides @IntoList(flatten = true)
                public static Set<Integer> setOfInts() { return null; }

                @Provides @IntoList(flatten = true)
                public List<Integer> listOfInts() { return null; }

                @Provides @IntoList(flatten = true)
                public Collection<Integer> collectionOfInts() { return null; }
            }
        """.trimIndent())

        givenKotlinSource("test.TestModuleKotlin", """
            import com.yandex.yatagan.*
   
            @Module
            class TestModuleKotlin {
                @Provides @IntoList(flatten = true) 
                fun setOfInts(): Set<Int> { throw NotImplementedError() }
                @Provides @IntoList(flatten = true)
                fun listOfInts(): List<Int> { throw NotImplementedError() }
                @Provides @IntoList(flatten = true)
                fun collectionOfInts(): Collection<Int> { throw NotImplementedError() }
            }
        """.trimIndent())

        givenKotlinSource("test.TestComponent", """
            import com.yandex.yatagan.*
            
            @Component(modules = [TestModule::class, TestModuleKotlin::class])
            interface TestComponent {
                val ints: List<Int>    
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `IntoMap basic test`() {
        givenJavaSource("test.MyApi", """
            public interface MyApi {}
        """.trimIndent())
        givenJavaSource("test.CustomClassKey", """
            import com.yandex.yatagan.IntoMap;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            
            @IntoMap.Key
            @Retention(RetentionPolicy.RUNTIME)
            public @interface CustomClassKey {
                Class<? extends MyApi> value();
            }
        """.trimIndent())
        givenJavaSource("test.MyEnum", """
            public enum MyEnum {
                ONE, TWO, THREE,
            }
        """.trimIndent())
        givenKotlinSource("test.KotlinConsumer", """
            import com.yandex.yatagan.*
            import javax.inject.*
            class KotlinConsumer @Inject constructor(
                map1: Map<Integer, String>,
                @Named("name") map2: Map<Class<*>, Integer>,
                map3: Map<Class<*>, Integer>,
                map4: Map<Class<out MyApi>, MyApi>,
                map5: Map<MyEnum, MyApi>,
                pm1: Provider<Map<Integer, String>>,
                @Named("name") pm2: Provider<Map<Class<*>, Integer>>,
                pm3: Provider<Map<Class<*>, Integer>>,
                lm4: Lazy<Map<Class<out MyApi>, MyApi>>,
                lm5: Lazy<Map<MyEnum, MyApi>>,
                mapOfProviders: Map<Class<out MyApi>, Provider<MyApi>>,
                @Named("name") kaboom: Provider<Map<Class<*>, Provider<Integer>>>,
            )
        """.trimIndent())
        givenJavaSource("test.JavaConsumer", """
            import java.util.Map;
            import javax.inject.Inject;
            import javax.inject.Provider;
            import javax.inject.Named;
            import com.yandex.yatagan.Lazy;
            
            public class JavaConsumer {
                @Inject public JavaConsumer(
                    Map<Integer, String> map1,
                    @Named("name") Map<Class<?>, Integer> map2,
                    Map<Class<?>, Integer> map3,
                    Map<Class<? extends MyApi>, MyApi> map4,
                    Map<MyEnum, MyApi> map5,
                    Provider<Map<Integer, String>> pm1,
                    @Named("name") Provider<Map<Class<?>, Integer>> pm2,
                    Provider<Map<Class<?>, Integer>> pm3,
                    Lazy<Map<Class<? extends MyApi>, MyApi>> lm4,
                    Lazy<Map<MyEnum, MyApi>> lm5,
                    Map<Class<? extends MyApi>, Provider<MyApi>> mapOfProviders
                ) {}
            }
        """.trimIndent())
        givenKotlinSource("test.CustomEnumKey", """
            import com.yandex.yatagan.*
            @IntoMap.Key
            @Retention(AnnotationRetention.RUNTIME)
            annotation class CustomEnumKey(val value: MyEnum = MyEnum.ONE)
        """.trimIndent())
        givenJavaSource("test.CustomEnumKeyJava", """
            import com.yandex.yatagan.IntoMap;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @IntoMap.Key
            @Retention(RetentionPolicy.RUNTIME)
            public @interface CustomEnumKeyJava {
                MyEnum value();
            }
        """.trimIndent())
        givenJavaSource("test.ComponentBase", """
            import java.util.Map;
            import javax.inject.Named;
            public interface ComponentBase {
                Map<Integer, String> getMap1Java();
                @Named("name") Map<Class<?>, Integer> getMap2Java();
                Map<Class<?>, Integer> getMap3Java();
                Map<Class<? extends MyApi>, MyApi> getMap4Java();
                Map<MyEnum, MyApi> getMap5Java();
                JavaConsumer getConsumer();
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class Impl1 : MyApi
            class Impl2 @Inject constructor() : MyApi
            class Impl3 @Inject constructor() : MyApi
            
            @Module
            object TestModule {
                @[Provides IntoMap IntKey(1)]
                fun string1(): String = "hello"
                @[Provides IntoMap IntKey(2)]
                fun string2(): String = "world"
            
                @[Provides Named("name") IntoMap ClassKey(Any::class)]
                fun int1(): Int = 1
                @[Provides Named("name") IntoMap ClassKey(String::class)]
                fun int2(): Int = 2
            
                @[Provides IntoMap ClassKey(TestModule::class)]
                fun int3(): Int = 3
            
                @[Provides IntoMap CustomClassKey(Impl1::class)]
                fun apiImpl1(): MyApi = Impl1()

                @[Provides IntoMap CustomEnumKey]
                fun one(): MyApi = Impl1()
                @[Provides IntoMap CustomEnumKeyJava(MyEnum.TWO)]
                fun two(): MyApi = Impl2()
                @[Provides IntoMap CustomEnumKey(MyEnum.THREE)]
                fun three(): MyApi = Impl3()
            }
            
            @Module
            interface TestBindings {
                @[Binds IntoMap CustomClassKey(Impl2::class)]
                fun apiImpl2(i: Impl2): MyApi
                
                @[Binds IntoMap CustomClassKey(Impl3::class)]
                fun apiImpl3(i: Impl3): MyApi
                
                @Multibinds
                fun emptyMap(): Map<String, Any>
                
                @Named("hello")
                @Multibinds
                fun emptyMap2(): Map<String, Any>
            }
            
            @Component(modules = [TestModule::class, TestBindings::class])
            interface TestComponent : ComponentBase {
                val map: Map<Int, String>
                @get:Named("name") val map2: Map<Class<*>, Int>
                val map3: Map<Class<*>, Int>
                val map4: Map<Class<out MyApi>, MyApi>
                val map5: Map<MyEnum, MyApi>
                
                val emptyMap: Map<String, Any>
                @get:Named("hello") val emptyMap2: Map<String, Any>
                
                val mapOfProviders: Map<Class<out MyApi>, Provider<MyApi>>
                
                val kotlinConsumer: KotlinConsumer
            }
            
            fun test() {
                val c: TestComponent = Yatagan.create(TestComponent::class.java)
                assert(c.map == mapOf(
                    1 to "hello",
                    2 to "world",
                ))
                assert(c.map2 == mapOf(
                    Any::class.java to 1,
                    String::class.java to 2,
                ))
                assert(c.map3 == mapOf(
                    TestModule::class.java to 3,
                ))
                assert(c.map4.size == 3)
                for ((clazz, instance) in c.map4) {
                    assert(clazz.isInstance(instance))
                }
                assert(c.map5.keys == MyEnum.values().toSet()) 
                assert(c.mapOfProviders.size == 3)
                assert(c.emptyMap.isEmpty())
                for ((clazz, instanceProvider) in c.mapOfProviders) {
                    assert(clazz.isInstance(instanceProvider.get()))
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `list bindings are inherited from super-components`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Module(subcomponents = [SubComponent::class, SubComponent2::class]) object RootModule {
                @[Provides IntoList(flatten = true)] fun zeroAndOne(): List<Number> = listOf(0, 1)
                @[Provides IntoList] fun two(): Number = 2.0
                @[Provides IntoList] fun three(): Number = 3.0f

                @[Provides IntoList] fun int1(): Int = -1
                @[Provides IntoList] fun int2(): Int = -2
                @[Provides IntoList] fun int3(): Int = -3

                @[Named("qualified") Provides IntoList] fun qInt1(): Int = 1
                @[Named("qualified") Provides IntoList] fun qInt2(): Int = 2
                @[Named("qualified") Provides IntoList] fun qInt3(): Int = 3
            }

            @Module object SubModule {
                @[Provides IntoList] fun four(): Number = 4L
                @[Provides IntoList] fun five(): Number = 5
                @[Provides IntoList(flatten = true)] fun sixAndSeven(): List<Number> = listOf(6, 7)
            }

            @Module object SubModule2 {
                @[Provides IntoList] fun p0(): Number = 10.0
                @[Provides IntoList] fun p1(): Number = 20.0
                
                @[Named("qualified") Provides IntoList] fun qInt1(): Int = 4
            }

            @Component(modules = [RootModule::class])
            interface RootComponent {
                val sub: SubComponent.Builder
                val sub2: SubComponent2.Builder
                
                @get:Named("qualified") val qInts: List<Int>
            }

            @Component(isRoot = false, modules = [SubModule::class])
            interface SubComponent {
                val numbers: List<Number>
                @get:Named("qualified") val qInts: List<Int>
                
                @Component.Builder interface Builder {
                    fun create(): SubComponent
                }
            }

            @Component(isRoot = false, modules = [SubModule2::class])
            interface SubComponent2 {
                val numbers: List<Number>
                @get:Named("qualified") val qInts: List<Int>
                
                @Component.Builder interface Builder {
                    fun create(): SubComponent2
                }
            }

            fun test() {
                val c: RootComponent = Yatagan.create(RootComponent::class.java)
                assert(c.qInts.toSet() == setOf(1, 2, 3))

                assert(c.sub.create().numbers.toSet() == setOf<Number>(0, 1, 2.0, 3.0f, 4L, 5, 6, 7))
                assert(c.sub.create().qInts.toSet() == setOf(1, 2, 3))

                assert(c.sub2.create().numbers.toSet() == setOf<Number>(0, 1, 2.0, 3.0f, 10.0, 20.0))
                assert(c.sub2.create().qInts.toSet() == setOf(1, 2, 3, 4))
             }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `map bindings are inherited from super-components`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Module(subcomponents = [SubComponent::class, SubComponent2::class]) object RootModule {
                @[Provides IntoMap IntKey(1)] fun one(): String = "one"
                @[Provides IntoMap IntKey(2)] fun two(): String = "two"

                @[Provides IntoMap IntKey(1)] fun int1(): Int = -1
                @[Provides IntoMap IntKey(2)] fun int2(): Int = -2

                @[Named("qualified") Provides IntoMap IntKey(1)] fun qInt1(): Int = 1
                @[Named("qualified") Provides IntoMap IntKey(2)] fun qInt2(): Int = 2
            }

            @Module object SubModule {
                @[Provides IntoMap IntKey(3)] fun three(): String = "three"
                @[Provides IntoMap IntKey(4)] fun four(): String = "four"
            }

            @Module object SubModule2 {
                @[Provides IntoMap IntKey(3)] fun p0(): String = "3/10" 
                @[Provides IntoMap IntKey(20)] fun p1(): String = "20"
                
                @[Named("qualified") Provides IntoMap IntKey(3)] fun qInt1(): Int = 3
            }

            @Component(modules = [RootModule::class])
            interface RootComponent {
                val sub: SubComponent.Builder
                val sub2: SubComponent2.Builder
                
                @get:Named("qualified") val qInts: Map<Int, Int>
            }

            @Component(isRoot = false, modules = [SubModule::class])
            interface SubComponent {
                val map: Map<Int, String>
                @get:Named("qualified") val qInts: Map<Int, Int>
                
                @Component.Builder interface Builder {
                    fun create(): SubComponent
                }
            }

            @Component(isRoot = false, modules = [SubModule2::class])
            interface SubComponent2 {
                val map: Map<Int, String>
                @get:Named("qualified") val qInts: Map<Int, Int>
                
                @Component.Builder interface Builder {
                    fun create(): SubComponent2
                }
            }

            fun test() {
                val c: RootComponent = Yatagan.create(RootComponent::class.java)
                assert(c.qInts == mapOf(1 to 1, 2 to 2))

                assert(c.sub.create().map == mapOf(1 to "one", 2 to "two", 3 to "three", 4 to "four"))
                assert(c.sub.create().qInts == mapOf(1 to 1, 2 to 2))

                assert(c.sub2.create().map == mapOf(1 to "one", 2 to "two", 3 to "3/10", 20 to "20"))
                assert(c.sub2.create().qInts == mapOf(1 to 1, 2 to 2, 3 to 3))
             }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `multi-bound set basic test`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class Features {
                companion object { var isEnabled = false }
                @Condition(Features::class, "Companion.isEnabled")
                annotation class Feature
            }
            
            interface Handler
            
            @Singleton
            class ClassA @Inject constructor (b: Optional<ClassB>) : Handler
            
            @Singleton
            @Conditional(Features.Feature::class)
            class ClassB @Inject constructor() : Handler
            
            @Singleton
            class ClassC @Inject constructor(c: ClassA) : Handler

            class ClassX : Handler
            class ClassY : Handler

            class Consumer @Inject constructor(handlers: Set<Handler>)
            
            @Module
            interface MyModule {
                @Binds @IntoSet fun a(i: ClassA): Handler
                @Binds @IntoSet fun b(i: ClassB): Handler
                @Binds @IntoSet fun c(i: ClassC): Handler
                
                @Multibinds fun emptySet(): Set<Number>
                
                companion object {
                    @get:Provides
                    val x = ClassX()

                    @get:Provides
                    val y = ClassY()

                    @Provides @IntoSet(flatten = true)
                    fun collection1(): Collection<Handler> = listOf(x, y)
                    
                    @Provides @IntoSet(flatten = true)
                    fun collection2(): Collection<Handler> = listOf(y)
                }
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                val a: ClassA
                val c: ClassC
                val consumer: Consumer
                val numbers: Set<Number>

                fun handlers(): Set<Handler>
            }

            fun test() {
                val c: TestComponent = Yatagan.create(TestComponent::class.java)
                assert(c.handlers() !== c.handlers())
                assert(c.handlers() == setOf(c.a, c.c, MyModule.x, MyModule.y))
                assert(c.numbers.isEmpty())
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}
