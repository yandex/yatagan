package com.yandex.daggerlite.testing

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
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            @Singleton class ClassA @Inject constructor (b: ClassB) : Create
            @Singleton class ClassB @Inject constructor() : Create
            @Singleton class ClassC @Inject constructor (a: ClassA) : Create
            class Consumer @Inject constructor(list: List<Create>, later: Provider<List<Create>>)
            
            @Module
            interface MyModule {
                @DeclareList fun createList(): Create
            
                @IntoList @Binds fun createClassA(a: ClassA): Create
                @IntoList @Binds fun createClassB(b: ClassB): Create
                @IntoList @Binds fun createClassC(c: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
                fun bootstrapLater(): Provider<List<Create>>
                val c: Consumer
                val c2: ConsumerJava
            }
            
            fun test() {
                val c = Dagger.create(TestComponent::class.java)
                
                val bootstrapList = c.bootstrap()
                assert(bootstrapList !== c.bootstrap())
                assert(bootstrapList[0] is ClassB) {"classB"}
                assert(bootstrapList[1] is ClassA) {"classA"}
                assert(bootstrapList[2] is ClassC) {"classC"}
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `class implements multiple interfaces`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
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
            
            @Module
            interface MyModule {                
                @DeclareList fun create(): Create
                @Binds @IntoList fun create(i: CreateA): Create
                @Binds @IntoList fun create(i: CreateB): Create
                @Binds @IntoList fun create(i: CreateDestroyA): Create
                @Binds @IntoList fun create(i: CreateDestroyB): Create
                @Binds @IntoList fun create(i: CreateDestroyC): Create
                @Binds @IntoList fun create(i: CreateDestroyD): Create
                
                @DeclareList fun destroy(): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyA): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyB): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyC): Destroy
                @Binds @IntoList fun destroy(i: CreateDestroyD): Destroy
                @Binds @IntoList fun activityDestroy(i: DestroyA): Destroy
                
                companion object {
                    @Provides fun createDestroyD() = CreateDestroyD()
                }
            }
            
            @Singleton
            @Component(modules = [MyModule::class])
            interface MyComponent {
                val bootstrapCreate: List<Create>
                val bootstrapDestroy: List<Destroy>
            }
            
            fun test() {
                val c = Dagger.create(MyComponent::class.java)
                val create = c.bootstrapCreate
                val destroy = c.bootstrapDestroy
            
                assert(create.size == 6)
                assert(create.map { it::class } == listOf(CreateA::class, CreateB::class, CreateDestroyA::class, 
                    CreateDestroyB::class, CreateDestroyC::class, CreateDestroyD::class))
                assert(destroy.size == 5)
                assert(destroy.map { it::class } == listOf(CreateDestroyA::class, CreateDestroyB::class, 
                    CreateDestroyC::class, CreateDestroyD::class, DestroyA::class))
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `list declaration binds empty list`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface Create
            
            @Module
            interface MyModule {
                @DeclareList fun create(): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }
            
            fun test() {
                assert(Dagger.create(TestComponent::class.java).bootstrap().isEmpty())
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `multi-bound list with conditional entries`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.IntoList
            import com.yandex.daggerlite.DeclareList
            
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
                @DeclareList fun create(): Create
                @Binds @IntoList fun create(i: ClassA): Create
                @Binds @IntoList fun create(i: ClassB): Create
                @Binds @IntoList fun create(i: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `flattening contribution`() {
        givenJavaSource("test.TestModule", """
            import com.yandex.daggerlite.IntoList;
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Provides;
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
            import com.yandex.daggerlite.*
   
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
            import com.yandex.daggerlite.*
            
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
            import com.yandex.daggerlite.IntoMap;
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
            import com.yandex.daggerlite.*
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
            import com.yandex.daggerlite.Lazy;
            
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
            import com.yandex.daggerlite.*
            @IntoMap.Key
            @Retention(AnnotationRetention.RUNTIME)
            annotation class CustomEnumKey(val value: MyEnum = MyEnum.ONE)
        """.trimIndent())
        givenJavaSource("test.CustomEnumKeyJava", """
            import com.yandex.daggerlite.IntoMap;
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
            import com.yandex.daggerlite.*
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
            }
            
            @Component(modules = [TestModule::class, TestBindings::class])
            interface TestComponent : ComponentBase {
                val map: Map<Int, String>
                @get:Named("name") val map2: Map<Class<*>, Int>
                val map3: Map<Class<*>, Int>
                val map4: Map<Class<out MyApi>, MyApi>
                val map5: Map<MyEnum, MyApi>
                
                val mapOfProviders: Map<Class<out MyApi>, Provider<MyApi>>
                
                val kotlinConsumer: KotlinConsumer
            }
            
            fun test() {
                val c: TestComponent = Dagger.create(TestComponent::class.java)
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
            import com.yandex.daggerlite.*
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
                val c: RootComponent = Dagger.create(RootComponent::class.java)
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
            import com.yandex.daggerlite.*
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
                val c: RootComponent = Dagger.create(RootComponent::class.java)
                assert(c.qInts == mapOf(1 to 1, 2 to 2))

                assert(c.sub.create().map == mapOf(1 to "one", 2 to "two", 3 to "three", 4 to "four"))
                assert(c.sub.create().qInts == mapOf(1 to 1, 2 to 2))

                assert(c.sub2.create().map == mapOf(1 to "one", 2 to "two", 3 to "3/10", 20 to "20"))
                assert(c.sub2.create().qInts == mapOf(1 to 1, 2 to 2, 3 to 3))
             }
        """.trimIndent())

        compileRunAndValidate()
    }
}
