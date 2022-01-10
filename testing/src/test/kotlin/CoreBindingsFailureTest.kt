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
                    listOf("test.TestComponent", "[entry-point] getFoo2", "@Inject test.Foo2", "[missing: java.lang.Object]"),
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
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
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
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB", "@Inject test.UnderComplex", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA"),
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
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
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
                    listOf("test.MyComponent", "[entry-point] getE1", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAandB", "@Inject test.UnderB"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB", "@Inject test.UnderB"),
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
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB", "@Inject test.UnderA", "@Inject test.UnderAorB"),
                    listOf("test.MyComponent", "[entry-point] getE2", "@Inject test.UnderB", "@Inject test.UnderComplex", "@Inject test.UnderA", "@Inject test.UnderAorB"),
                    listOf("test.MyComponent", "[entry-point] getObject1", "@Provides test.MyModule::provideObject(test.UnderA): @javax.inject.Named(\"error\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB"),
                    listOf("test.MyComponent", "[entry-point] getObject2", "@Provides test.MyModule::provideObject2(test.UnderA [OptionalProvider]): @javax.inject.Named(\"ok1\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB"),
                    listOf("test.MyComponent", "[entry-point] getObject3", "@Provides test.MyModule::provideObject3(test.UnderA): @javax.inject.Named(\"ok2\") java.lang.Object", "@Inject test.UnderA", "@Inject test.UnderAorB"),
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
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `component features`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            interface Heater
            
            @Conditional([A::class])
            class ElectricHeater @Inject constructor() : Heater
            
            @Conditional([B::class])
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
            
            @Conditional([A::class])
            @Component(isRoot = false)
            interface SubComponentA {
                val electric: ElectricHeater  // ok
                val gas: GasHeater  // error
                
                @Component.Builder
                interface Creator { fun create(): SubComponentA }
            
                fun injectConsumer(consumer: Consumer)
            }
            
            @Conditional([B::class])
            @Component(isRoot = false)
            interface SubComponentB {
                val electric: ElectricHeater  // error
                val gas: GasHeater  // ok
                
                @Component.Builder
                interface Creator { fun create(): SubComponentB }
            
                fun injectConsumer(consumer: Consumer)
            }
        """.trimIndent())

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                Errors.`incompatible condition scope for entry-point`(
                    aCondition = "[test.Foo.isEnabledA]", bCondition = "[unconditional]",
                    binding = "@Inject test.ElectricHeater", component = "test.RootComponent",
                ),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getElectric"),
                    listOf("test.RootComponent", "[injector-fun] injectConsumer", "[member-to-inject] setElectricHeater")
                ),
            ))
            withError(formatMessage(
                Errors.`incompatible condition scope for entry-point`(
                    aCondition = "[test.Foo.isEnabledB]", bCondition = "[unconditional]",
                    binding = "@Inject test.GasHeater", component = "test.RootComponent",
                ),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getGas"),
                    listOf("test.RootComponent", "[injector-fun] injectConsumer", "[member-to-inject] setGasHeater"),
                ),
            ))
            withError(formatMessage(
                Errors.`incompatible condition scope for entry-point`(
                    aCondition = "[test.Foo.isEnabledB]", bCondition = "[test.Foo.isEnabledA]",
                    binding = "@Inject test.GasHeater", component = "test.SubComponentA",
                ),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponentA", "[entry-point] getGas"),
                    listOf("test.RootComponent", "test.SubComponentA", "[injector-fun] injectConsumer", "[member-to-inject] setGasHeater"),
                ),
            ))
            withError(formatMessage(
                Errors.`incompatible condition scope for entry-point`(
                    aCondition = "[test.Foo.isEnabledA]", bCondition = "[test.Foo.isEnabledB]",
                    binding = "@Inject test.ElectricHeater", component = "test.SubComponentB",
                ),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponentB", "[entry-point] getElectric"),
                    listOf("test.RootComponent", "test.SubComponentB", "[injector-fun] injectConsumer", "[member-to-inject] setElectricHeater")
                ),
            ))
            withNoMoreErrors()
            withNoWarnings()
            // @formatter:on
        }
    }

    @Test
    fun `invalid features & variants`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            annotation class NotAFeature
            annotation class NotAFeature2
            annotation class NotADimension
            @ComponentFlavor(dimension = NotADimension::class)
            annotation class InvalidFlavor
            annotation class NotAFlavor
            @ComponentFlavor(dimension = NotADimension::class)
            annotation class InvalidFlavor2
            
            @Conditional([NotAFeature::class, NotAFeature2::class],
                         onlyIn = [InvalidFlavor::class, NotAFlavor::class])
            class ClassA @Inject constructor()
            @Module(subcomponents = [AnotherComponent::class]) interface RootModule
            @Component(variant = [InvalidFlavor::class], modules = [RootModule::class])
            interface RootComponent {
                val a: Optional<ClassA>
            }
            @Component(variant = [InvalidFlavor::class, InvalidFlavor2::class, NotAFlavor::class], isRoot = false)
            interface AnotherComponent { @Component.Builder interface C { fun c(): AnotherComponent } }
        """.trimIndent())

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`no conditions on feature`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA", "[feature] test.NotAFeature"),
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA", "[feature] test.NotAFeature2"),
                ),
            ))
            withError(formatMessage(
                message = Errors.`declaration is not annotated with @ComponentVariantDimension`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "Variant{...}", "[flavor] test.InvalidFlavor", "[dimension] test.NotADimension"),
                    listOf("test.RootComponent", "test.AnotherComponent", "Variant{...}", "[flavor] test.InvalidFlavor2", "[dimension] test.NotADimension"),
                    listOf("test.RootComponent", "test.AnotherComponent", "Variant{...}", "[flavor] test.InvalidFlavor", "[dimension] test.NotADimension"),
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA", "[flavor] test.InvalidFlavor", "[dimension] test.NotADimension"),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting or duplicate flavors for dimension`(dimension = "[dimension] test.NotADimension"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.AnotherComponent", "Variant{...}"),
                ),
                notes = listOf(
                    "Conflicting flavor: `[flavor] test.InvalidFlavor`",
                    "Conflicting flavor: `[flavor] test.InvalidFlavor2`",
                    "Conflicting flavor: `[flavor] test.InvalidFlavor`",
                )
            ))
            withError(formatMessage(
                message = Errors.`declaration is not annotated with @ComponentFlavor`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.AnotherComponent", "Variant{...}", "[flavor] test.NotAFlavor"),
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA", "[flavor] test.NotAFlavor"),
                ),
            ))
            withError(formatMessage(
                message = Errors.`missing component variant dimension`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.AnotherComponent", "Variant{...}", "[flavor] test.NotAFlavor", "[missing dimension]"),
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA", "[flavor] test.NotAFlavor", "[missing dimension]"),
                ),
            ))
            withError(formatMessage(
                message = Errors.`undeclared dimension in variant`(dimension = "[missing dimension]"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getA", "@Inject test.ClassA"),
                ),
            ))
            // @formatter:on
            withNoWarnings()
            withNoMoreErrors()
        }
    }

    @Test
    fun `invalid conditions`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            object Foo {
                val foo: String
            }

            @Condition(Foo::class, "!hello")
            annotation class Invalid1
            @Condition(Int::class, "#invalid")
            annotation class Invalid2
            @Condition(Foo::class, "foo")
            annotation class Invalid3

            @Conditional([
                Invalid1::class,
                Invalid2::class,
                Invalid3::class,
            ])
            class ClassA @Inject constructor()
            
            @Component
            interface MyComponent {
                val a: Optional<ClassA>
            }
        """.trimIndent())

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`invalid condition`("#invalid"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[entry-point] getA", "@Inject test.ClassA", "[!test.Foo.hello && <invalid> && test.Foo.foo]", "<invalid>")
                ),
            ))
            withError(formatMessage(
                message = Errors.`invalid condition - missing member`(name = "hello", type = "test.Foo"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[entry-point] getA", "@Inject test.ClassA", "[!test.Foo.hello && <invalid> && test.Foo.foo]", "!test.Foo.hello")
                ),
            ))
            withError(formatMessage(
                message = Errors.`invalid condition - unable to reach boolean`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[entry-point] getA", "@Inject test.ClassA", "[!test.Foo.hello && <invalid> && test.Foo.foo]", "test.Foo.foo")
                ),
            ))
            // @formatter:on
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `conflicting bindings`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            @Qualifier
            annotation class MyQualifier(val named: Named)
            @Qualifier
            annotation class Numbered(val value: Int)
            const val Hello = "hello"
            
            interface Dependency {
                val myString: String
            }
            
            @Module
            class MyModule {
                @MyQualifier(Named("hello"))
                @Provides fun provideObject(): Any { return Any() }
                
                @MyQualifier(Named(value = Hello))
                @Provides fun provideObject2(): Any { return Any() }
            
                @Provides fun c1() : MyComponent1 = throw AssertionError()
                @Named("flag") @Provides fun bool() = true
                @Provides fun dep(): Dependency = throw AssertionError()
            }
            
            @Module(includes = [MyModule::class], subcomponents = [SubComponent::class])
            class MyModule2 {
                @Provides @IntoList fun one(): Number = 1L
                @Provides @IntoList fun two(): Number = 2.0
                @Provides @IntoList fun three(): Number = 3f
            
                @Provides fun numbers(): List<Number> = emptyList()
                @Provides fun builder(): SubComponent.Builder = throw AssertionError()
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
            @Component(isRoot = false)
            interface SubComponent {
                @Component.Builder interface Builder { fun create(): SubComponent }
            }
        """.trimIndent())

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "@test.MyQualifier(named=@javax.inject.Named(\"hello\")) java.lang.Object"),
                encounterPaths = listOf(
                    listOf("test.MyComponent1"),
                    listOf("test.MyComponent2"),
                ),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule::provideObject(): @test.MyQualifier(named=@javax.inject.Named(\"hello\")) java.lang.Object"),
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule::provideObject2(): @test.MyQualifier(named=@javax.inject.Named(\"hello\")) java.lang.Object"),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "test.MyComponent1"),
                encounterPaths = listOf(listOf("test.MyComponent1")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule::c1(): test.MyComponent1"),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.componentInstance("test.MyComponent1")),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "@javax.inject.Named(\"flag\") java.lang.Boolean"),
                encounterPaths = listOf(listOf("test.MyComponent2")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule::bool(): @javax.inject.Named(\"flag\") java.lang.Boolean"),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.instance("[setter] setFlag(@javax.inject.Named(\"flag\") java.lang.Boolean)")),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "test.Dependency"),
                encounterPaths = listOf(listOf("test.MyComponent2")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule::dep(): test.Dependency"),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.componentDependencyInstance("test.Dependency")),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "java.util.List<java.lang.Number>"),
                encounterPaths = listOf(listOf("test.MyComponent2")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule2::numbers(): java.util.List<java.lang.Number>"),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.instance("[setter] setNumbers(java.util.List<java.lang.Number>)")),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.multibinding(
                        elementType = "java.util.List<java.lang.Number>",
                        contributions = listOf(
                            "@Provides test.MyModule2::one(): java.lang.Number",
                            "@Provides test.MyModule2::two(): java.lang.Number",
                            "@Provides test.MyModule2::three(): java.lang.Number",
                        ),
                    )),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "test.SubComponent.Builder"),
                encounterPaths = listOf(listOf("test.MyComponent2")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = "@Provides test.MyModule2::builder(): test.SubComponent.Builder"),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.subcomponentFactory("[creator] test.SubComponent.Builder")),
                ),
            ))
            withError(formatMessage(
                message = Errors.`conflicting bindings`(`for` = "java.lang.String"),
                encounterPaths = listOf(listOf("test.MyComponent2")),
                notes = listOf(
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.componentDependencyEntryPoint(
                        entryPoint = "test.Dependency::getMyString(): java.lang.String",
                    )),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.instance("[setter] withString(java.lang.String)")),
                    Strings.Notes.`duplicate binding`(binding = Strings.Bindings.instance("[setter] withAnotherString(java.lang.String)")),
                ),
            ))
            // @formatter:on
            withNoMoreErrors()
            withNoWarnings()
        }
    }

    @Test
    fun `manual framework type usage`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
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

        failsToCompile {
            // @formatter:off
            withError(formatMessage(
                message = Errors.`missing binding`(`for` = "com.yandex.daggerlite.Optional<test.WithInject>"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "[entry-point] getO1", "[missing: com.yandex.daggerlite.Optional<test.WithInject>]"),
                    listOf("test.RootComponent", "[entry-point] getO2", "[missing: com.yandex.daggerlite.Optional<test.WithInject>]"),
                    listOf("test.RootComponent", "[entry-point] getO3", "[missing: com.yandex.daggerlite.Optional<test.WithInject>]"),
                ),
                notes = listOf(
                    Strings.Notes.`nested framework type`("com.yandex.daggerlite.Optional<test.WithInject>")
                ),
            ))
            withError(formatMessage(
                message = Errors.`framework type is manually managed`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.MyModule", "@Provides test.MyModule::illegalOptional(): com.yandex.daggerlite.Optional<java.lang.Object>", "com.yandex.daggerlite.Optional<java.lang.Object>"),
                    listOf("test.RootComponent", "test.MyModule", "@Provides test.MyModule::illegalLazy(): com.yandex.daggerlite.Lazy<java.lang.Integer>", "com.yandex.daggerlite.Lazy<java.lang.Integer>"),
                    listOf("test.RootComponent", "test.MyModule", "@Provides test.MyModule::illegalProvider(): javax.inject.Provider<java.lang.String>", "javax.inject.Provider<java.lang.String>"),
                    listOf("test.RootComponent", "[entry-point] getO1", "com.yandex.daggerlite.Optional<test.WithInject>"),
                    listOf("test.RootComponent", "[entry-point] getO2", "com.yandex.daggerlite.Optional<test.WithInject>"),
                    listOf("test.RootComponent", "[entry-point] getO3", "com.yandex.daggerlite.Optional<test.WithInject>"),
                    listOf("test.RootComponent", "[creator] test.RootComponent.Builder", "[param] create(.., flagProvider: javax.inject.Provider<java.lang.Boolean>, ..)"),
                    listOf("test.RootComponent", "[creator] test.RootComponent.Builder", "[param] create(.., optionalFloat: com.yandex.daggerlite.Optional<java.lang.Float>, ..)"),
                ),
            ))
            withNoMoreErrors()
            withNoWarnings()
            // @formatter:off
        }
    }

    @Test
    fun `multi-threading status mismatch`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            
            @Module(subcomponents = SubComponent1::class) interface RootModule
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

        failsToCompile {
            withError(formatMessage(
                message = Errors.`multi-threading status mismatch`("test.RootComponent"),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent1")),
            ))
            withNoMoreErrors()
            withNoWarnings()
        }
    }
}