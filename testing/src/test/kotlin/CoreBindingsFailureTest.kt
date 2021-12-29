package com.yandex.daggerlite.testing

import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.Strings.formatMessage
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

    @Test
    fun `missing dependency`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Inject
            
            class Foo @Inject constructor(obj: Any, foo: Lazy<Foo2>)
            class Foo2 @Inject constructor(obj: Any)
            class ToInject {
                @set:Inject
                var obj: Any
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

        failsToCompile {
            withError(formatMessage(
                message = Errors.`missing binding`("java.lang.Object"),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.TestComponent", "[entry-point] getFoo", "@Inject test.Foo", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[entry-point] getFoo", "@Inject test.Foo", "@Inject test.Foo2", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[entry-point] getHello", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[entry-point] getBye", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[injector-fun] inject", "[member-to-inject] setObj", "[missing: java.lang.Object]"),
                    // @formatter:on
                ),
                notes = listOf(Strings.Notes.`no known way to infer a binding`()),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `no compatible scope for inject`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import com.yandex.daggerlite.Module
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

        failsToCompile {
            withError(formatMessage(
                message = Errors.`no matching scope for binding`(
                    binding = "@Inject test.Foo",
                    scope = "@javax.inject.Singleton",
                ),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.RootComponent", "test.SubComponent", "[entry-point] getFooForSub", "[missing: test.Foo]"),
                    listOf("test.RootComponent", "[entry-point] getFooForRoot", "[missing: test.Foo]"),
                    // @formatter:on
                )
            ))
            withNoWarnings()
            withNoMoreErrors()
        }
    }

    @Test
    fun `no compatible scope for provision`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Provides
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

        failsToCompile {
            val binding =
                "@Provides test.RootModule::provideFoo(java.lang.Integer): @javax.inject.Named(\"foo\") test.Foo"
            withError(formatMessage(
                message = Errors.`no matching scope for binding`(
                    binding = binding,
                    scope = "@javax.inject.Singleton"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponent", "[entry-point] getFooForSub", binding),
                )
            ))
            withError(formatMessage(
                message = Errors.`missing binding`(`for` = "java.lang.Integer"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponent", "[entry-point] getFooForSub", binding, "[missing: java.lang.Integer]"),
                ),
                notes = listOf(Strings.Notes.`no known way to infer a binding`())
            ))
            withNoWarnings()
            withNoMoreErrors()
        }
    }

    @Test
    fun `invalid bindings`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            @Module
            class TestModule {
                @Provides @IntoList 
                fun bindOne(): Int = 1
                @Provides @IntoList
                fun bindTwo(): Int = 2
                @Provides @IntoList(flatten = true)
                fun bindThreeForFive(): Int = 3
                @Provides fun hello()
                @Binds fun hello2()
            }
            @Module
            interface TestModule2 {
                @Provides fun provides(): Long
                @Binds fun bindListToString(list: List<Int>): String
            }
            @Component(modules = [TestModule::class, TestModule2::class])
            interface TestComponent {
                fun getInts(): List<Int>
            }
        """.trimIndent())

        failsToCompile {
            withError(formatMessage(
                message = Errors.`binds param type is incompatible with return type`(
                    param = "java.util.List<java.lang.Integer>",
                    returnType = "java.lang.String",
                ),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.TestModule2", "@Binds test.TestModule2::bindListToString(java.util.List<java.lang.Integer>): java.lang.String"),
                )
            ))
            withError(formatMessage(
                message = Errors.`provides must not be abstract`(),
                encounterPaths = listOf(
                    listOf("test.TestComponent",
                        "test.TestModule2",
                        "@Provides test.TestModule2::provides(): java.lang.Long"),
                )
            ))
            withError(formatMessage(
                message = Errors.`binding must not return void`(),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.TestModule", "@Provides test.TestModule::hello(): [invalid]"),
                    listOf("test.TestComponent", "test.TestModule", "@Binds test.TestModule::hello2(): [invalid]"),
                )
            ))
            withError(formatMessage(
                message = Errors.`binds must be abstract`(),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.TestModule", "@Binds test.TestModule::hello2(): [invalid]"),
                )
            ))
            withError(formatMessage(
                message = Errors.`invalid flattening multibinding`("int"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.TestModule",
                        "@Provides test.TestModule::bindThreeForFive(): java.lang.Integer")
                )
            ))
            withNoWarnings()
            withNoMoreErrors()
        }
    }

    @Test
    fun `incompatible conditions`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            object Foo { 
                const val isEnabledA = true 
                const val isEnabledB = true 
                const val isEnabledC = true 
            }

            @Condition(Foo::class, "isEnabledA") annotation class A
            @Condition(Foo::class, "isEnabledB") annotation class B
            @Condition(Foo::class, "isEnabledC") annotation class C

            @AllConditions([
                Condition(Foo::class, "isEnabledA"),
                Condition(Foo::class, "isEnabledB"),
            ])
            annotation class AandB
            
            @AnyCondition([
                Condition(Foo::class, "isEnabledA"),
                Condition(Foo::class, "isEnabledB"),
            ])
            annotation class AorB

            @Conditional([A::class, B::class])
            class UnderAandB @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>)

            @Conditional([AorB::class, A::class])
            class UnderAorB @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>/*error*/)

            @Conditional([AandB::class, AorB::class])
            class UnderComplex @Inject constructor(a: Lazy<UnderA>, b: Provider<UnderB>)

            @Conditional([A::class]) class UnderA @Inject constructor(
                a: UnderAandB,  // error
                ab: UnderAorB,  // ok
            )
            @Conditional([AorB::class, B::class]) class UnderB @Inject constructor(
                b: UnderA,  // error
                c: Lazy<UnderComplex>,  // error
            )

            @Module
            class MyModule {
                @Provides @Named("error")
                fun provideObject(c: UnderA): Any = c
                @Provides @Named("ok1")
                fun provideObject2(c: Optional<Provider<UnderA>>): Any = c
                @Provides([Conditional([A::class])]) @Named("ok2")
                fun provideObject2(c: UnderA): Any = c
            }

            @Component(modules = [MyModule::class])
            interface MyComponent {
                val e1: Optional<UnderA>
                val e2: Optional<UnderB>
                @get:Named("error") val object1: Any
                @get:Named("ok1") val object2: Any
                @get:Named("ok2") val object3: Any
            }
        """.trimIndent())

        failsToCompile {
            withError(formatMessage(
                message = Errors.`incompatible condition scope`(
                    aCondition = "[test.Foo.isEnabledA && test.Foo.isEnabledB]",
                    bCondition = "[test.Foo.isEnabledA]",
                    a = "test.UnderAandB",
                    b = "@Inject test.UnderA",
                ),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB", "@Inject test.UnderComplex", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject2(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA"),
                    // @formatter:on
                ),
            ))
            withError(formatMessage(
                message = Errors.`incompatible condition scope`(
                    aCondition = "[test.Foo.isEnabledA]",
                    bCondition = "[(test.Foo.isEnabledA || test.Foo.isEnabledB) && test.Foo.isEnabledB]",
                    a = "test.UnderA",
                    b = "@Inject test.UnderB",
                ),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB", "@Inject test.UnderComplex", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB"),
                    // @formatter:on
                ),
            ))
            withError(formatMessage(
                message = Errors.`incompatible condition scope`(
                    aCondition = "[test.Foo.isEnabledA && test.Foo.isEnabledB && (test.Foo.isEnabledA || test.Foo.isEnabledB)]",
                    bCondition = "[(test.Foo.isEnabledA || test.Foo.isEnabledB) && test.Foo.isEnabledB]",
                    a = "test.UnderComplex",
                    b = "@Inject test.UnderB",
                ),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB", "@Inject test.UnderComplex", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB"),
                    // @formatter:on
                ),
            ))
            withError(formatMessage(
                message = Errors.`incompatible condition scope`(
                    aCondition = "[(test.Foo.isEnabledA || test.Foo.isEnabledB) && test.Foo.isEnabledB]",
                    bCondition = "[(test.Foo.isEnabledA || test.Foo.isEnabledB) && test.Foo.isEnabledA]",
                    a = "test.UnderB",
                    b = "@Inject test.UnderAorB",
                ),
                encounterPaths = listOf(
                    // @formatter:off
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB"),
                    // @formatter:on
                ),
            ))
            withError(formatMessage(
                // @formatter:off
                message = Errors.`incompatible condition scope`(
                    aCondition = "[test.Foo.isEnabledA]",
                    bCondition = "[unconditional]",
                    a = "test.UnderA",
                    b = "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object",
                ),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object"),
                ),
                // @formatter:on
            ))
        }
    }
}