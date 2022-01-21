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
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.DeclareList
            import com.yandex.daggerlite.IntoList
            import javax.inject.Singleton
            import javax.inject.Inject
            import javax.inject.Provider
            
            interface Create
            
            @Singleton class ClassA @Inject constructor (b: ClassB) : Create
            @Singleton class ClassB @Inject constructor() : Create
            @Singleton class ClassC @Inject constructor (a: ClassA) : Create
            
            @Module
            interface MyModule {
                @DeclareList(orderByDependency = true) fun createList(): Create
            
                @IntoList @Binds fun createClassA(a: ClassA): Create
                @IntoList @Binds fun createClassB(b: ClassB): Create
                @IntoList @Binds fun createClassC(c: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
                fun bootstrapLater(): Provider<List<Create>>
            }
            
            fun test() {
                val c = DaggerTestComponent.create()
                
                val bootstrapList = c.bootstrap()
                assert(bootstrapList !== c.bootstrap())
                assert(bootstrapList[0] is ClassB) {"classB"}
                assert(bootstrapList[1] is ClassA) {"classA"}
                assert(bootstrapList[2] is ClassC) {"classC"}
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")

            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `class implements multiple interfaces`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.IntoList
            import com.yandex.daggerlite.DeclareList
            
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
                @DeclareList(orderByDependency = true) fun create(): Create
                @Binds @IntoList fun create(i: CreateA): Create
                @Binds @IntoList fun create(i: CreateB): Create
                @Binds @IntoList fun create(i: CreateDestroyA): Create
                @Binds @IntoList fun create(i: CreateDestroyB): Create
                @Binds @IntoList fun create(i: CreateDestroyC): Create
                @Binds @IntoList fun create(i: CreateDestroyD): Create
                
                @DeclareList(orderByDependency = true) fun destroy(): Destroy
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
                val c = DaggerMyComponent.create()
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

        compilesSuccessfully {
            generatesJavaSources("test.DaggerMyComponent")
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `list declaration binds empty list`() {
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
            
            interface Create
            
            @Module
            interface MyModule {
                @DeclareList(orderByDependency = true) fun create(): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }
            
            fun test() {
                assert(DaggerTestComponent.create().bootstrap().isEmpty())
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
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
            
            object Features {
                var isEnabled = false
            
                @Condition(Features::class, "isEnabled")
                annotation class Feature
            }
            
            interface Create
            
            @Singleton
            class ClassA @Inject constructor (b: Optional<ClassB>) : Create
            
            @Singleton
            @Conditional([Features.Feature::class])
            class ClassB @Inject constructor() : Create
            
            @Singleton
            class ClassC @Inject constructor (c: ClassA) : Create
            
            @Module
            interface MyModule {
                @DeclareList(orderByDependency = true) fun create(): Create
                @Binds @IntoList fun create(i: ClassA): Create
                @Binds @IntoList fun create(i: ClassB): Create
                @Binds @IntoList fun create(i: ClassC): Create
            }
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                fun bootstrap(): List<Create>
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }
}
