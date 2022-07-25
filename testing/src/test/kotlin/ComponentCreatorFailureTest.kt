package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ComponentCreatorFailureTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `missing creator`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            @Module(subcomponents = [SubComponent::class, NotAComponent::class, AnotherRootComponent::class])
            interface RootModule
            
            @Component(modules = [RootModule::class])
            interface RootComponent
            interface MyDependency
            @Module class MyModule(@get:Provides val obj: Any)
            @Component(isRoot = false, dependencies = [MyDependency::class], modules = [MyModule::class])
            abstract class SubComponent {
                abstract val obj: Any
            }
            interface NotAComponent
            @Component
            interface AnotherRootComponent
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid member-injector`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface Injectee
            
            @Component
            interface MyComponent {
                fun misc(i: Injectee, extra: Int)
                fun inject(i: Injectee): Int
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `missing entities in creator`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            @Module
            class TriviallyConstructableModule {
                @get:Provides
                val number: Short get() = 0
            }
            @Module
            class RequiresInstance(private val i: Long) {
                @get:Provides
                val number: Long get() = i
            }
            @Module
            class Unknown(private val i: Double) {
                @get:Provides
                val number: Double get() = i
            }
            class MyDependency {
                val notGonnaBeUsed: Optional<Any> = Optional.empty()
            }
            @Component(dependencies = [
                MyDependency::class,
            ], modules = [
                TriviallyConstructableModule::class,
                RequiresInstance::class,
            ])
            interface MyComponent {
                @Component.Builder
                abstract class Builder {
                    abstract fun create(module: Unknown): MyComponent
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid component creator`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface FooBaseBase {
                @BindsInstance
                fun setShort(i: Short): FooBaseBase
            }
            
            interface FooBase : FooBaseBase {
                @BindsInstance 
                fun setChar(i: Char): FooBase
            }
            
            @Module
            interface Unnecessary
            
            @Component
            interface MyComponent {
                @Component.Builder
                interface Foo : FooBase {
                    fun setInt(i: Int)
                    @BindsInstance fun setLong(i: Long)
                    @BindsInstance fun setDouble(i: Double): Foo
                    fun setString(@BindsInstance i: String): String
                    fun setModule(m: Unnecessary)
                    fun create()
                }
            
                @Component.Builder
                interface Builder {
                    fun create(): MyComponent
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}