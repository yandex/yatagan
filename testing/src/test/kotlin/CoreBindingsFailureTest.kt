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
            withNoMoreErrors()
            withNoWarnings()
        }
    }
}