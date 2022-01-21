package com.yandex.daggerlite.testing

import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.Strings.formatMessage
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
        features = givenSourceSet {
            givenKotlinSource("test.features", """
                import com.yandex.daggerlite.Condition                

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
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            class ClassA @Inject constructor(b: ClassB)
            class ClassB @Inject constructor(a: ClassA)
            
            @Component
            interface RootComponent {
                val b: ClassB
            }
        """.trimIndent())

        failsToCompile {
            withError(formatMessage(
                message = Errors.`dependency loop`(listOf(
                    "test.ClassB" to "@Inject test.ClassB",
                    "test.ClassA" to "@Inject test.ClassA",
                )),
                encounterPaths = listOf(listOf("test.RootComponent")),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `dependency loop with alias`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
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

        failsToCompile {
            withError(formatMessage(
                message = Errors.`dependency loop`(listOf(
                    "test.ApiA" to "[alias] @Binds test.MyModule::a(test.ClassA): test.ApiA",
                    "test.ClassA" to "@Inject test.ClassA",
                    "test.ApiB" to "[alias] @Binds test.MyModule::b(test.ClassB): test.ApiB",
                    "test.ClassB" to "@Inject test.ClassB",
                )),
                encounterPaths = listOf(listOf("test.RootComponent")),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `self-dependent bindings`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface ApiA
            interface ApiB
            @Conditional([A::class])
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

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`self-dependent binding`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getA", "[invalid] @Binds test.MyModule::a(test.AImpl, test.ApiA): test.ApiA"),
                    listOf("test.RootComponent", "[entry-point] getB", "[invalid] @Provides test.MyModule::b(test.ApiA, test.ApiB): test.ApiB"),
                ),
            ))
            // @formatter:on
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test(timeout = 10_000)
    fun `invalid cross-alias loop`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
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

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`binds param type is incompatible with return type`(
                    param = "test.ApiB", returnType = "test.ApiA",
                ),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.MyModule", "@Binds test.MyModule::a(test.ApiB): test.ApiA")
                )
            ))
            withError(formatMessage(
                message = Errors.`dependency loop`(chain = listOf(
                    "test.ApiA" to "[alias] @Binds test.MyModule::a(test.ApiB): test.ApiA",
                    "test.ApiB" to "[alias] @Binds test.MyModule::b(test.ApiA): test.ApiB",
                )),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getA", "[invalid] [alias] @Binds test.MyModule::a(test.ApiB): test.ApiA")
                )
            ))
            // @formatter:on
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `looped component hierarchy`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
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

        failsToCompile { 
            withError(formatMessage(
                message = Errors.`component hierarchy loop`(),
                encounterPaths = listOf(listOf("test.MyRootComponent", "test.MySubComponentA", "test.MySubComponentB", "test.MySubComponentA"))
            ))
            withError(formatMessage(
                message = Errors.`declaration is not annotated with @Module`(),
                encounterPaths = listOf(listOf("test.MyRootComponent", "test.MySubComponentA", "test.MySubComponentB", "test.NotAModule"))
            ))
            withError(formatMessage(
                message = Errors.`duplicate component scope`(scope = "@javax.inject.Singleton"),
                encounterPaths = listOf(listOf("test.MyRootComponent", "test.MySubComponentA", "test.MySubComponentB")),
                notes = listOf(
                    Strings.Notes.`duplicate scope component`("test.MyRootComponent"),
                    Strings.Notes.`duplicate scope component`("test.MySubComponentB"),
                ),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }
}