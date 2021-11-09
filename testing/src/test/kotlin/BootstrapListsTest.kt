package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class BootstrapListsTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `basic test`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.BootstrapInterface
            import com.yandex.daggerlite.BootstrapList
            
            @BootstrapInterface
            interface Create
            
            @Singleton class ClassA @Inject constructor (b: ClassB) : Create
            @Singleton class ClassB @Inject constructor() : Create
            @Singleton class ClassC @Inject constructor (a: ClassA) : Create
            
            @Module(bootstrap = [
                ClassA::class,
                ClassB::class,
                ClassC::class,
            ])
            interface MyModule
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                @BootstrapList
                fun bootstrap(): List<Create>
                @BootstrapList
                fun bootstrapLater(): Provider<List<Create>>
            }

            fun test() {
                val c = DaggerTestComponent()
                
                val bootstrapList = c.bootstrap()
                assert(bootstrapList !== c.bootstrap())
                assert(bootstrapList[0] is ClassB)
                assert(bootstrapList[1] is ClassA)
                assert(bootstrapList[2] is ClassC)
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
            
            import com.yandex.daggerlite.BootstrapInterface
            import com.yandex.daggerlite.BootstrapList
            
            @BootstrapInterface interface Create
            @BootstrapInterface interface Destroy

            class CreateA @Inject constructor() : Create
            @Singleton class CreateB @Inject constructor(a: CreateA) : Create

            @Singleton open class CreateDestroyA @Inject constructor() : Create, Destroy
            interface ActivityDestroy: Destroy
            @Singleton class CreateDestroyB @Inject constructor() : Create, ActivityDestroy
            @Singleton open class CreateDestroyC @Inject constructor() : CreateDestroyA()
            @Singleton class CreateDestroyD : CreateDestroyC()

            @Singleton class DestroyA @Inject constructor() : ActivityDestroy

            @Module(bootstrap = [
                CreateA::class,
                CreateB::class,
                CreateDestroyA::class,
                CreateDestroyB::class,
                CreateDestroyC::class,
                CreateDestroyD::class,
                ActivityDestroy::class,
            ])
            interface MyModule {
                @Binds fun activityDestroy(i: DestroyA): ActivityDestroy
                
                companion object {
                    @Provides fun createDestroyD() = CreateDestroyD()
                }
            }
    
            @Singleton
            @Component(modules = [MyModule::class])
            interface MyComponent {
                @get:BootstrapList
                val bootstrapCreate: List<Create>
                @get:BootstrapList
                val bootstrapDestroy: List<Destroy>
            }

            fun test() {
                val c = DaggerMyComponent()
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
    fun `bootstrap list with conditional entries`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.BootstrapInterface
            import com.yandex.daggerlite.BootstrapList
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional

            object Features {
                var isEnabled = false

                @Condition(Features::class, "isEnabled")
                annotation class Feature
            }
            
            @BootstrapInterface
            interface Create
            
            @Singleton
            class ClassA @Inject constructor (b: ClassB) : Create
            
            @Singleton
            @Conditional([Features.Feature::class])
            class ClassB @Inject constructor() : Create
            
            @Singleton
            class ClassC @Inject constructor (c: ClassA) : Create
            
            @Module(bootstrap = [
                ClassA::class,
                ClassB::class,
                ClassC::class,
            ])
            interface MyModule
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                @BootstrapList
                fun bootstrap(): List<Create>
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }
}
