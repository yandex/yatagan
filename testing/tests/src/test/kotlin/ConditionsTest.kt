package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ConditionsTest(
    driverProvider: Provider<CompileTestDriver>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    private val flavors by lazy {
        SourceSet {
            givenKotlinSource("test.Flavors", """
                import com.yandex.yatagan.ComponentVariantDimension
                import com.yandex.yatagan.ComponentFlavor

                @ComponentVariantDimension
                annotation class ProductType {
                    @ComponentFlavor(ProductType::class)
                    annotation class Browser

                    @ComponentFlavor(ProductType::class)
                    annotation class SearchApp
                }

                @ComponentVariantDimension
                annotation class DeviceType {
                    @ComponentFlavor(DeviceType::class)
                    annotation class Phone
                    
                    @ComponentFlavor(DeviceType::class)
                    annotation class Tablet
                }

                @ComponentVariantDimension
                annotation class ActivityType {
                    @ComponentFlavor(ActivityType::class)
                    annotation class Main

                    @ComponentFlavor(ActivityType::class)
                    annotation class Custom

                    @ComponentFlavor(ActivityType::class)
                    annotation class Alice
                }
            """
            )
        }
    }

    private val features by lazy {
        SourceSet {
            givenKotlinSource("test.Features", """
                import com.yandex.yatagan.Condition
                import javax.inject.Singleton

                class FeatureC {
                    var isEnabled: Boolean = false
                }

                object Features {
                    @get:JvmName("fooBar") @get:JvmStatic
                    var enabledA: Boolean = false 
                    @get:JvmStatic
                    var isEnabledB: Boolean = false
                    @get:JvmStatic
                    val FeatureC = FeatureC()
                }

                @Singleton
                interface Conditions {
                    @Condition(Features::class, condition = "fooBar")
                    annotation class FeatureA
    
                    @Condition(Features::class, condition = "isEnabledB")
                    annotation class FeatureB
    
                    @Condition(Features::class, condition = "getFeatureC.isEnabled")
                    annotation class FeatureC
                }
            """
            )
        }
    }

    @Test
    fun `conditions test`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Singleton
            @Conditional(Conditions.FeatureA::class, Conditions.FeatureB::class)
            class MyClass @Inject constructor(a: ClassA, b: ClassB)

            @Conditional(Conditions.FeatureA::class)
            class ClassA @Inject constructor()

            @Conditional(Conditions.FeatureB::class)
            class ClassB @Inject constructor()

            @Singleton
            @Component
            interface TestComponent {
                val opt: Optional<MyClass>
                val provider: Optional<Provider<MyClass>>
            }

            fun test() {
                val component = Yatagan.create(TestComponent::class.java)
                assert(!component.opt.isPresent)
                Features.enabledA = true
                assert(!Yatagan.create(TestComponent::class.java).opt.isPresent)
                Features.isEnabledB = true
                val new = Yatagan.create(TestComponent::class.java)
                assert(new.opt.isPresent)
                assert(new.opt.get() === new.provider.get().get())

                // Condition is not re-requested.
                assert(!component.opt.isPresent)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `flavors test`() {
        includeFromSourceSet(features)
        includeFromSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Provider
            import javax.inject.Singleton
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Condition
            import com.yandex.yatagan.Conditional
            import com.yandex.yatagan.Conditionals
            import com.yandex.yatagan.Optional

            @Conditional(Conditions.FeatureA::class, onlyIn = [DeviceType.Phone::class])
            @Conditional(Conditions.FeatureB::class, onlyIn = [DeviceType.Tablet::class])
            class MyClass @Inject constructor()

            @Singleton
            @Component(variant = [DeviceType.Phone::class])
            interface TestPhoneComponent {
                val opt: Optional<MyClass>
                val provider: Optional<Provider<MyClass>>
                val direct: Optional<MyClass>
            }

            @Singleton
            @Component(variant = [DeviceType.Tablet::class])
            interface TestTabletComponent {
                val opt: Optional<MyClass>
                val provider: Optional<Provider<MyClass>>
                val direct: Optional<MyClass>
            }

            fun test() {
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `flavors test - ruled-out variant binding requested as optional`() {
        includeFromSourceSet(features)
        includeFromSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Conditional(onlyIn = [DeviceType.Phone::class])
            class MyPhoneSpecificClass @Inject constructor()
            
            @Conditional(onlyIn = [DeviceType.Tablet::class])
            class MyTabletSpecificClass @Inject constructor()

            @Singleton @Component(variant = [DeviceType.Phone::class])
            interface TestPhoneComponent {
                val phone: Optional<MyPhoneSpecificClass>
                val tablet: Optional<MyTabletSpecificClass>
            }

            @Singleton @Component(variant = [DeviceType.Tablet::class])
            interface TestTabletComponent {
                val phone: Optional<MyPhoneSpecificClass>
                val tablet: Optional<MyTabletSpecificClass>
            }

            fun test() {
                val phone = Yatagan.create(TestPhoneComponent::class.java)
                val tablet = Yatagan.create(TestTabletComponent::class.java)

                assert(phone.phone.isPresent)
                assert(!phone.tablet.isPresent)

                assert(!tablet.phone.isPresent)
                assert(tablet.tablet.isPresent)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `flavors test - subcomponents under feature`() {
        includeFromSourceSet(features)
        includeFromSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Scope
            annotation class ActivityScoped

            @ComponentFlavor(ActivityType::class)
            annotation class MyFeatureActivity

            @Module(subcomponents = [MyFeatureActivityComponent::class])
            interface MyFeatureActivityInstallationModule

            interface TestComponent {
                val myFeatureActivity: Optional<MyFeatureActivityComponent.Factory>
            }

            @Singleton
            @Component(
                modules = [MyFeatureActivityInstallationModule::class],
                variant = [DeviceType.Phone::class],
            )
            interface TestPhoneComponent : TestComponent

            @Singleton 
            @Component(
                modules = [MyFeatureActivityInstallationModule::class],
                variant = [DeviceType.Tablet::class],
            )
            interface TestTabletComponent : TestComponent

            @ActivityScoped
            @Conditional(Conditions.FeatureB::class, onlyIn = [DeviceType.Tablet::class])
            @Component(isRoot = false, variant = [MyFeatureActivity::class])
            interface MyFeatureActivityComponent {
                @Component.Builder
                interface Factory {
                    fun create(): MyFeatureActivityComponent
                }
            }

            fun test() {
                Features.isEnabledB = true
                val phone: TestComponent = Yatagan.create(TestPhoneComponent::class.java)
                val tablet: TestComponent = Yatagan.create(TestTabletComponent::class.java)

                assert(!phone.myFeatureActivity.isPresent)
                assert(tablet.myFeatureActivity.isPresent)
            }
        """
        )

        compileRunAndValidate()
    }

    @Test
    fun `conditions in component hierarchy`() {
        includeFromSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Conditional
            import com.yandex.yatagan.Optional
            
            @Conditional(Conditions.FeatureA::class) class ClassA @Inject constructor()
            @Conditional(Conditions.FeatureB::class) class ClassB @Inject constructor(a: Optional<ClassA>)
            
            @Module(subcomponents = [TestSubComponent::class]) interface SubcomponentInstallationModule
            
            @Singleton
            @Component(modules = [SubcomponentInstallationModule::class])
            interface TestComponent {
                val a: Optional<ClassA>
                val sub: TestSubComponent.Factory
            }
            
            @Component(isRoot = false)
            interface TestSubComponent {
                val b: Optional<ClassB>
                @Component.Builder 
                interface Factory { fun create(): TestSubComponent }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `@Binds with multiple alternatives`() {
        includeFromSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            interface SomeApiBase
            
            interface SomeApi : SomeApiBase
            
            @Conditional(Conditions.FeatureA::class)
            class BaseImpl @Inject constructor() : SomeApiBase
            
            @Conditional(Conditions.FeatureA::class)
            class ImplA @Inject constructor() : SomeApi
            
            @Conditional(Conditions.FeatureB::class)
            class ImplB @Inject constructor() : SomeApi
            
            class Stub @Inject constructor() : SomeApi
            
            @Module
            interface MyModule {
                @Binds @Named("v1")
                fun alias(i: ImplA): SomeApi
                
                @Binds @Named("v2")
                fun exhaustive2alt(i: ImplA, stub: Stub): SomeApi
                
                @Binds @Named("v3")
                fun exhaustive3alt(i: ImplA, i2: ImplB, stub: Stub): SomeApi
                
                @Singleton
                @Binds @Named("v4")
                fun nonExhaustive(i: ImplA, i2: ImplB): SomeApi
                
                @Binds
                fun base(@Named("v4") i: SomeApi, i2: BaseImpl): SomeApiBase
            }
            
            @Singleton
            @Component(modules = [MyModule::class])
            interface TestComponent {
                @get:Named("v1") val apiV1: Optional<SomeApi>
                @get:Named("v2") val apiV2: Optional<SomeApi>
                @get:Named("v3") val apiV3: Optional<SomeApi>
                @get:Named("v4") val apiV4: Optional<SomeApi>
                
                @get:Named("v1") val apiV1Lazy: Optional<Lazy<SomeApi>>
                @get:Named("v2") val apiV2Lazy: Optional<Lazy<SomeApi>>
                @get:Named("v3") val apiV3Lazy: Optional<Lazy<SomeApi>>
                @get:Named("v4") val apiV4Lazy: Optional<Lazy<SomeApi>>
                
                @get:Named("v1") val apiV1Provider: Optional<Provider<SomeApi>>
                @get:Named("v2") val apiV2Provider: Optional<Provider<SomeApi>>
                @get:Named("v3") val apiV3Provider: Optional<Provider<SomeApi>>
                @get:Named("v4") val apiV4Provider: Optional<Provider<SomeApi>>
                
                val base: Optional<SomeApiBase>
            }
            
            fun test() {
                Features.isEnabledB = true
                val c = Yatagan.create(TestComponent::class.java)
                assert(!c.apiV1.isPresent)
                assert(c.apiV2.get() is Stub)
                assert(c.apiV3.get() is ImplB)
                assert(c.apiV4.get() is ImplB)
                
                assert(!c.apiV1Lazy.isPresent)
                assert(c.apiV2Lazy.get().get() is Stub)
                assert(c.apiV3Lazy.get().get() is ImplB)
                assert(c.apiV4Lazy.get().get() is ImplB)
                
                assert(!c.apiV1Provider.isPresent)
                assert(c.apiV2Provider.get().get() is Stub)
                assert(c.apiV3Provider.get().get() is ImplB)
                assert(c.apiV4Provider.get().get() is ImplB)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `conditional provide - basic case`() {
        includeFromSourceSet(features)
        includeFromSourceSet(flavors)
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Api
            class Impl: Api
            
            @Module
            object MyModule {
                @Provides(
                    Conditional(Conditions.FeatureA::class, onlyIn = [ActivityType.Main::class]),
                    Conditional(Conditions.FeatureB::class), // in the rest
                )
                fun provideApi(): Api {
                    return Impl()
                }
                
                @Named
                @Provides(
                    Conditional(onlyIn = [ActivityType.Main::class]),
                    // Nowhere else
                )
                fun provideNamedApi(): Api {
                    return Impl()
                }
            }
            
            @Component(modules = [MyModule::class], variant = [ActivityType.Main::class])
            interface TestMainComponent {
                val api: Optional<Api>
                val apiLazy: Optional<Lazy<Api>>
                
                @get:Named
                val namedApi: Optional<Api>
            }
            
            @Component(modules = [MyModule::class], variant = [ActivityType.Custom::class])
            interface TestCustomComponent {
                val api: Optional<Api>
                val apiLazy: Optional<Lazy<Api>>
                
                @get:Named
                val namedApi: Optional<Api>
            }
            
            fun test() {
                assert(Yatagan.create(TestMainComponent::class.java).namedApi.isPresent)
                assert(!Yatagan.create(TestCustomComponent::class.java).namedApi.isPresent)
            
                assert(!Yatagan.create(TestMainComponent::class.java).api.isPresent)
            
                Features.isEnabledB = true
                assert(!Yatagan.create(TestMainComponent::class.java).api.isPresent)
            
                Features.enabledA = true
                Features.isEnabledB = false
                assert(Yatagan.create(TestMainComponent::class.java).api.isPresent)
                assert(!Yatagan.create(TestCustomComponent::class.java).api.isPresent)
                
                Features.enabledA = false
                Features.isEnabledB = true
                assert(Yatagan.create(TestCustomComponent::class.java).api.isPresent)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `flavors inheritance in component hierarchy`() {
        includeFromSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Conditional(onlyIn = [ProductType.Browser::class])
            class Impl @Inject constructor()
            
            @Module(subcomponents = [MyComponent::class])
            interface MyModule
            
            @Component(isRoot = false)
            interface MyComponent {
                val impl: Optional<Impl>
                
                @Component.Builder
                interface Factory {
                    fun create(): MyComponent
                }
            }
            
            @Component(modules = [MyModule::class], variant = [ProductType.Browser::class])
            interface MyBrowserComponent {
                val myC: MyComponent.Factory
            }
            
            @Component(modules = [MyModule::class], variant = [ProductType.SearchApp::class])
            interface MySearchAppComponent {
                val myC: MyComponent.Factory
            }
            
            fun test() {
                val browserC = Yatagan.create(MyBrowserComponent::class.java)
                val searchAppC = Yatagan.create(MySearchAppComponent::class.java)
                
                assert(browserC.myC.create().impl.isPresent)
                assert(!searchAppC.myC.create().impl.isPresent)
            }

        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `complex condition expression`() {
        includeFromSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*            
            import javax.inject.*
            
            private const val EnabledB = "isEnabledB" 
            
            class SomeClass {
                companion object Conditions {
                    var someCondition1: Boolean = false
            
                    lateinit var someCondition6: java.lang.Boolean
                    @JvmField
                    var someCondition2: Boolean = false
                    @JvmStatic
                    var someCondition3: Boolean = false
                    @get:JvmStatic
                    var someCondition4: Boolean = false
                    @JvmStatic
                    fun someCondition5(): Boolean = false
                }
            }
            
            @Condition(Features::class, "fooBar")
            @Condition(Features::class, "isEnabledB")
            @AnyCondition(
                Condition(Features::class, "getFeatureC.isEnabled"),                                
                Condition(Features::class, "isEnabledB"),
                Condition(SomeClass::class, "Conditions.getSomeCondition1"),
                Condition(SomeClass::class, "someCondition2"),
                Condition(SomeClass::class, "getSomeCondition3"),
                Condition(SomeClass::class, "getSomeCondition4"),
                Condition(SomeClass::class, "someCondition5"),
                Condition(SomeClass::class, "someCondition6"),
                Condition(SomeClass::class, "Conditions.getSomeCondition6"),
            )
            annotation class ComplexFeature1
            
            @AnyCondition(
                Condition(Features::class, "fooBar"),
            )
            @AnyCondition(
                Condition(Features::class, EnabledB),
            )
            @AnyCondition(
                Condition(Features::class, "getFeatureC.isEnabled"),                                
                Condition(Features::class, condition = EnabledB),                                
            )
            @AnyCondition(
                Condition(Features::class, "getFeatureC.isEnabled"),                                
                Condition(value = Features::class, condition = "isEnabledB"),                                
            )            
            annotation class ComplexFeature2
            
            @Conditional(ComplexFeature1::class)               
            class ClassA @Inject constructor(b: ClassB)
            @Conditional(ComplexFeature2::class)
            class ClassB @Inject constructor(a: Provider<ClassA>)
            
            @Component            
            interface MyComponent {
                val a: Optional<ClassA>                        
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `flavor-based alternative binding in component hierarchy`() {
        includeFromSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Api
            @Conditional(onlyIn = [ProductType.Browser::class])
            class ImplA @Inject constructor() : Api
            @Conditional(onlyIn = [ProductType.SearchApp::class])
            class ImplB @Inject constructor() : Api
            
            @Module(subcomponents = [MyApiComponent::class])
            interface MyModule {
                @Binds
                fun api(i: ImplA, i2: ImplB): Api
            }
            
            @Component(isRoot = false)
            interface MyApiComponent {
                val api: Optional<Api>
                val apiProvider: Optional<Provider<Api>>
                
                @Component.Builder
                interface Factory {
                    fun create(): MyApiComponent
                }
            }
            
            @Component(modules = [MyModule::class], variant = [ProductType.Browser::class])
            interface MyBrowserComponent {
                val apiC: MyApiComponent.Factory
            }
            
            @Component(modules = [MyModule::class], variant = [ProductType.SearchApp::class])
            interface MySearchAppComponent {
                val apiC: MyApiComponent.Factory
            }
            
            @ComponentFlavor(ProductType::class)
            annotation class MyProductType
            
            @Component(modules = [MyModule::class], variant = [MyProductType::class])
            interface MyProductComponent {
                val apiC: MyApiComponent.Factory
            }
            
            fun test() {
                val browserC = Yatagan.create(MyBrowserComponent::class.java)
                val searchAppC = Yatagan.create(MySearchAppComponent::class.java)
                val myProductC = Yatagan.create(MyProductComponent::class.java)
                
                assert(browserC.apiC.create().api.get() is ImplA)
                assert(searchAppC.apiC.create().api.get() is ImplB)
                assert(!myProductC.apiC.create().api.isPresent)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `lazy condition evaluation`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            var disabled1Requested = false
            var disabled2Requested = false

            object Features {
                val notReached: Boolean get() = throw AssertionError("Not reached")
                val disabled: Boolean get() {
                    disabled1Requested = true
                    return false
                }

                val notReached2: Boolean get() = throw AssertionError("Not reached")
                val disabled2: Boolean get() {
                    disabled2Requested = true
                    return false
                }
            }

            @AllConditions(
                Condition(Features::class, condition = "INSTANCE.getDisabled"),
                Condition(Features::class, condition = "INSTANCE.getNotReached"),
            )
            annotation class A

            @AllConditions(
                Condition(Features::class, condition = "INSTANCE.getDisabled2"),
                Condition(Features::class, condition = "INSTANCE.getNotReached2"),
            )
            annotation class B

            
            @Conditional(A::class)
            class ClassA @Inject constructor()

            @Conditional(B::class)
            class ClassB @Inject constructor()

            @Component
            interface MyComponent { 
                val a: Optional<ClassA> 
                val b: Optional<ClassB> 
            }

            fun test() {
                assert(!disabled1Requested && !disabled2Requested)
                val c: MyComponent = Yatagan.create(MyComponent::class.java)
                assert(disabled1Requested && disabled2Requested)

                assert(!c.a.isPresent)
                assert(!c.b.isPresent)
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `const val conditions`() {
        // Disable for KSP, because file facades can't be resolved for now
        // TODO: Enable for KSP once the issues are fixed.
        assumeFalse(backendUnderTest == Backend.Ksp)

        givenPrecompiledModule(SourceSet {
            givenKotlinSource("test.CompiledCondition", """
                object CompiledCondition {
                    const val HELLO = true
                }
                // top level
                const val FOO = true
            """.trimIndent())
        })

        givenJavaSource("test.IsEnabled0", """
            import com.yandex.yatagan.Condition;
            
            @Condition(value = CompiledConditionKt.class, condition = "FOO")
            @interface IsEnabled3 {}
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            object Constants {
                const val IS_ENABLED = true
            }
            
            @Condition(CompiledCondition::class, "HELLO")
            annotation class IsEnabled
            
            @Condition(Constants::class, "IS_ENABLED")
            annotation class IsEnabled2
            
            @Conditional(IsEnabled::class, IsEnabled2::class, IsEnabled3::class)
            class TestClass @Inject constructor()
            
            @Component interface TestComponent {
                val test: Optional<TestClass>
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `non-static conditions`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class FeatureProvider(
                var isEnabledA: Boolean,
                var isEnabledB: Boolean,
                var isEnabledC: Boolean,
            ) {
                @Condition(FeatureProvider::class, "isEnabledA") annotation class A
                @Condition(FeatureProvider::class, "isEnabledB") annotation class B
                @Condition(FeatureProvider::class, "isEnabledC") annotation class C
            }

            class FeatureProvider2 @Inject constructor() {
                val isEnabledD: Boolean get() = true

                @Condition(FeatureProvider2::class, "isEnabledD") annotation class D
            }

            interface F3 {
                val isEnabledE: Boolean
                
                @Condition(F3::class, "isEnabledE") annotation class E
            }

            @Singleton
            @Conditional(FeatureProvider.C::class)
            class FeatureProvider3 @Inject constructor() : F3 {
                override var isEnabledE = false
            }
            
            @Conditional(FeatureProvider.A::class)
            class ClassA @Inject constructor()

            @Conditional(FeatureProvider.A::class, FeatureProvider.B::class)
            class ClassB @Inject constructor(c: Optional<ClassC>, a: ClassA)

            @Conditional(FeatureProvider.C::class, FeatureProvider2.D::class, F3.E::class)
            class ClassC @Inject constructor()

            @Module
            interface TestModule {
                @Binds
                fun alias(i: FeatureProvider3): F3
            }

            @Singleton
            @Component(modules = [TestModule::class])
            interface TestComponent {
                val a: Optional<ClassA>
                val b: Optional<ClassB>
                val c: Optional<ClassC>
                
                val f3: Optional<FeatureProvider3>

                @Component.Builder
                interface Builder {
                    fun create(
                        @BindsInstance features: FeatureProvider,
                    ): TestComponent
                }
            }

            fun test() {
                val factory: TestComponent.Builder = Yatagan.builder(TestComponent.Builder::class.java)
                val features = FeatureProvider(
                    isEnabledA = false,
                    isEnabledB = true,
                    isEnabledC = false,
                )
                // Create component - features are not touched
                val component = factory.create(features)

                // Can change features
                features.isEnabledA = true
                // Query A - feature A should be queried and saved
                assert(component.a.isPresent)
                features.isEnabledA = false  // Shouldn't affect anything
                assert(component.a.isPresent)  // Still present

                features.isEnabledB = false  // Change feature B
                assert(!component.b.isPresent)  // B is absent
                features.isEnabledB = false  // Shouldn't affect anything
                assert(!component.b.isPresent)  // B is still absent

                features.isEnabledC = true  // Change feature C
                assert(component.f3.isPresent)
                component.f3.get().isEnabledE = true  // Change feature E

                assert(component.c.isPresent)  // C is present
                features.isEnabledC = false  // Shouldn't affect anything
                component.f3.get().isEnabledE = false   // Shouldn't affect anything
                assert(component.c.isPresent)  // C is still present
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}