package com.yandex.daggerlite.testing

import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.Strings.formatMessage
import org.junit.Before
import org.junit.Ignore
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
            withError(formatMessage(
                message = Errors.`self-dependent binding`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getA", "[invalid] @Binds test.MyModule::a(test.AImpl, test.ApiA): test.ApiA"),
                    listOf("test.RootComponent", "[entry-point] getB", "[invalid] @Provides test.MyModule::b(test.ApiA, test.ApiB): test.ApiB"),
                ),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    @Ignore("Broken")
    fun `invalid cross-alias loop`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface ApiA
            interface ApiB
            
            @Module
            interface MyModule {
                @Binds fun b(a: ApiA): ApiB
                @Binds fun a(b: ApiB): ApiA
            }
            
            @Component(modules = [MyModule::class])
            interface RootComponent {
                val a: ApiA
                val b: ApiB
            }
        """.trimIndent())

        failsToCompile {
            withNoMoreErrors()
            withNoWarnings()
        }
    }
}