package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class GraphLoopsFailureTest(
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
                
                @Condition(Foo::class, "isEnabledA") annotation class A
                @Condition(Foo::class, "isEnabledB") annotation class B
                @Condition(Foo::class, "isEnabledC") annotation class C
            """.trimIndent())
        }
    }

    @Test
    fun `simple dependency loop`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class ClassA @Inject constructor(b: ClassB)
            class ClassB @Inject constructor(a: ClassA)
            
            @Component
            interface RootComponent {
                val b: ClassB
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `dependency loop with alias`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface ApiA
            interface ApiB
            class ClassA @Inject constructor(b: ApiB): ApiA
            class ClassB @Inject constructor(a: ApiA): ApiB
            
            @Module
            interface MyModule {
                @Binds fun a(a: ClassA): ApiA
                @Binds fun b(b: ClassB): ApiB
            }
            
            @Component(modules = [MyModule::class])
            interface RootComponent {
                val a: ApiA
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `self-dependent bindings`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface ApiA
            interface ApiB
            @Conditional(A::class)
            class AImpl @Inject constructor() : ApiA
            
            @Module
            interface MyModule {
                @Binds fun a(i: AImpl, a: ApiA): ApiA
                companion object {
                    @Provides fun b(a: ApiA, b: ApiB): ApiB = throw AssertionError()
                }
            }
            
            @Component(modules = [MyModule::class])
            interface RootComponent {
                val a: ApiA
                val b: ApiB
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test(timeout = 10_000)
    fun `invalid cross-alias loop`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface ApiA : ApiB
            interface ApiB
            
            @Module
            interface MyModule {
                @Binds fun b(a: ApiA): ApiB
                @Binds fun a(b: ApiB): ApiA
            }
            
            @Component(modules = [MyModule::class])
            interface RootComponent {
                val a: ApiA
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `looped component hierarchy`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            interface NotAModule

            @Singleton
            @Component(modules = [MyRootComponent.RootModule::class])
            interface MyRootComponent {
                @Module(subcomponents = [MySubComponentA::class]) interface RootModule
            }

            @Component(isRoot = false, modules = [MySubComponentA.SubModule::class])
            interface MySubComponentA {
                @Module(subcomponents = [MySubComponentB::class]) interface SubModule
                @Component.Builder interface Builder { fun create(): MySubComponentA }
            }

            @Singleton
            @Component(isRoot = false, modules = [MySubComponentB.SubModule::class, NotAModule::class])
            interface MySubComponentB {
                @Module(subcomponents = [MySubComponentA::class]) interface SubModule
                @Component.Builder interface Builder { fun create(): MySubComponentB }
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}