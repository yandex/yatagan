package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ConditionsTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    private val flavors by lazy {
        givenSourceSet {
            givenKotlinSource("test.Flavors", """
                import com.yandex.daggerlite.ComponentVariantDimension
                import com.yandex.daggerlite.ComponentFlavor

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
        givenSourceSet {
            givenKotlinSource("test.Features", """
                import com.yandex.daggerlite.Condition
                import javax.inject.Singleton

                class FeatureC {
                    var isEnabled: Boolean = false
                }

                object Features {
                    @get:JvmName("fooBar")
                    var enabledA: Boolean = false 
                    var isEnabledB: Boolean = false
                    val FeatureC = FeatureC()
                }

                @Singleton
                interface Conditions {
                    @Condition(Features::class, condition = "enabledA")
                    annotation class FeatureA
    
                    @Condition(Features::class, condition = "isEnabledB")
                    annotation class FeatureB
    
                    @Condition(Features::class, condition = "FeatureC.isEnabled")
                    annotation class FeatureC
                }
            """
            )
        }
    }

    @Test
    fun `conditions test`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Provider
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional

            @Singleton
            @Conditional([Conditions.FeatureA::class, Conditions.FeatureB::class])
            class MyClass @Inject constructor(a: ClassA, b: ClassB)

            @Conditional([Conditions.FeatureA::class])
            class ClassA @Inject constructor()

            @Conditional([Conditions.FeatureB::class])
            class ClassB @Inject constructor()

            @Singleton
            @Component
            interface TestComponent {
                val opt: Optional<MyClass>
                val provider: Optional<Provider<MyClass>>
            }

            fun test() {
                val component = DaggerTestComponent.create()
                assert(!component.opt.isPresent)
                Features.enabledA = true
                assert(!DaggerTestComponent.create().opt.isPresent)
                Features.isEnabledB = true
                val new = DaggerTestComponent.create()
                assert(new.opt.isPresent)
                assert(new.opt.get() === new.provider.get().get())

                // Condition is not re-requested.
                assert(!component.opt.isPresent)
            }
        """
        )

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
            inspectGeneratedClass("test.TestCaseKt") { case ->
                case["test"](null)
            }
        }
    }

    @Test
    fun `flavors test`() {
        useSourceSet(features)
        useSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Provider
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Conditionals
            import com.yandex.daggerlite.Optional

            @Conditional([Conditions.FeatureA::class], onlyIn = [DeviceType.Phone::class])
            @Conditional([Conditions.FeatureB::class], onlyIn = [DeviceType.Tablet::class])
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

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestPhoneComponent")
            generatesJavaSources("test.DaggerTestTabletComponent")
            inspectGeneratedClass("test.TestCaseKt") { case ->
                case["test"](null)
            }
        }
    }

    @Test
    fun `flavors test - ruled-out variant binding requested as optional`() {
        useSourceSet(features)
        useSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Conditionals
            import com.yandex.daggerlite.Optional

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
                val phone = DaggerTestPhoneComponent.create()
                val tablet = DaggerTestTabletComponent.create()

                assert(phone.phone.isPresent)
                assert(!phone.tablet.isPresent)

                assert(!tablet.phone.isPresent)
                assert(tablet.tablet.isPresent)
            }
        """
        )

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestPhoneComponent")
            generatesJavaSources("test.DaggerTestTabletComponent")
            inspectGeneratedClass("test.TestCaseKt") { case ->
                case["test"](null)
            }
        }
    }

    @Test
    fun `flavors test - subcomponents under feature`() {
        useSourceSet(features)
        useSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Scope
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Condition
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Conditionals
            import com.yandex.daggerlite.ComponentFlavor
            import com.yandex.daggerlite.Optional

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
            @Conditional([Conditions.FeatureB::class], onlyIn = [DeviceType.Tablet::class])
            @Component(isRoot = false, variant = [MyFeatureActivity::class])
            interface MyFeatureActivityComponent {
                @Component.Builder
                interface Factory {
                    fun create(): MyFeatureActivityComponent
                }
            }

            fun test() {
                Features.isEnabledB = true
                val phone: TestComponent = DaggerTestPhoneComponent.create()
                val tablet: TestComponent = DaggerTestTabletComponent.create()

                assert(!phone.myFeatureActivity.isPresent)
                assert(tablet.myFeatureActivity.isPresent)
            }
        """
        )

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestPhoneComponent")
            generatesJavaSources("test.DaggerTestTabletComponent")
            inspectGeneratedClass("test.TestCaseKt") { case ->
                case["test"](null)
            }
        }
    }

    @Test
    fun `conditions in component hierarchy`() {
        useSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional
            
            @Conditional([Conditions.FeatureA::class]) class ClassA @Inject constructor()
            @Conditional([Conditions.FeatureB::class]) class ClassB @Inject constructor(a: Optional<ClassA>)
            
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

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }

    @Test
    fun `@Binds with multiple alternatives`() {
        useSourceSet(features)
        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Named
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional
            import javax.inject.Provider
            import com.yandex.daggerlite.Lazy

            interface SomeApiBase
            
            interface SomeApi : SomeApiBase
            
            @Conditional([Conditions.FeatureA::class])
            class BaseImpl @Inject constructor() : SomeApiBase
            
            @Conditional([Conditions.FeatureA::class])
            class ImplA @Inject constructor() : SomeApi
            
            @Conditional([Conditions.FeatureB::class])
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
                val c = DaggerTestComponent.create()
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

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `conditional provide - basic case`() {
        useSourceSet(features)
        useSourceSet(flavors)
        givenKotlinSource("test.TestCase", """
            import javax.inject.Named
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Provides
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.Optional
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Lazy
            
            interface Api
            class Impl: Api
            
            @Module
            object MyModule {
                @Provides([
                    Conditional([Conditions.FeatureA::class], onlyIn = [ActivityType.Main::class]),
                    Conditional([Conditions.FeatureB::class]), // in the rest
                ])
                fun provideApi(): Api {
                    return Impl()
                }
                
                @Named
                @Provides([
                    Conditional(onlyIn = [ActivityType.Main::class]),
                    // Nowhere else
                ])
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
                assert(DaggerTestMainComponent.create().namedApi.isPresent)
                assert(!DaggerTestCustomComponent.create().namedApi.isPresent)
            
                assert(!DaggerTestMainComponent.create().api.isPresent)
            
                Features.isEnabledB = true
                assert(!DaggerTestMainComponent.create().api.isPresent)
            
                Features.enabledA = true
                Features.isEnabledB = false
                assert(DaggerTestMainComponent.create().api.isPresent)
                assert(!DaggerTestCustomComponent.create().api.isPresent)
                
                Features.enabledA = false
                Features.isEnabledB = true
                assert(DaggerTestCustomComponent.create().api.isPresent)
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `flavors inheritance in component hierarchy`() {
        useSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.ComponentFlavor
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional
            import com.yandex.daggerlite.Condition
            
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
                val browserC = DaggerMyBrowserComponent.create()
                val searchAppC = DaggerMySearchAppComponent.create()
                
                assert(browserC.myC.create().impl.isPresent)
                assert(!searchAppC.myC.create().impl.isPresent)
            }

        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerMyBrowserComponent")
            generatesJavaSources("test.DaggerMySearchAppComponent")

            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `complex condition expression`() {
        useSourceSet(features)

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*            
            import javax.inject.*
            
            private const val EnabledB = "isEnabledB" 
            
            @Condition(Features::class, "enabledA")
            @Condition(Features::class, "isEnabledB")
            @AnyCondition([
                Condition(Features::class, "FeatureC.isEnabled"),                                
                Condition(Features::class, "isEnabledB"),                                
            ])
            annotation class ComplexFeature1
            
            @AnyCondition([
                Condition(Features::class, "enabledA"),
            ])
            @AnyCondition([
                Condition(Features::class, EnabledB),
            ])
            @AnyCondition([
                Condition(Features::class, "FeatureC.isEnabled"),                                
                Condition(Features::class, condition = EnabledB),                                
            ])
            @AnyCondition([
                Condition(Features::class, "FeatureC.isEnabled"),                                
                Condition(value = Features::class, condition = "isEnabledB"),                                
            ])            
            annotation class ComplexFeature2
            
            @Conditional([ComplexFeature1::class])               
            class ClassA @Inject constructor(b: ClassB)
            @Conditional([ComplexFeature2::class])
            class ClassB @Inject constructor(a: Provider<ClassA>)
            
            @Component            
            interface MyComponent {
                val a: Optional<ClassA>                        
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            withNoErrors()
        }
    }

    @Test
    fun `flavor-based alternative binding in component hierarchy`() {
        useSourceSet(flavors)

        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Module
            import com.yandex.daggerlite.ComponentFlavor
            import com.yandex.daggerlite.Conditional
            import com.yandex.daggerlite.Optional
            import javax.inject.Provider
            
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
                val browserC = DaggerMyBrowserComponent.create()
                val searchAppC = DaggerMySearchAppComponent.create()
                val myProductC = DaggerMyProductComponent.create()
                
                assert(browserC.apiC.create().api.get() is ImplA)
                assert(searchAppC.apiC.create().api.get() is ImplB)
                assert(!myProductC.apiC.create().api.isPresent)
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerMyBrowserComponent")
            generatesJavaSources("test.DaggerMySearchAppComponent")
            generatesJavaSources("test.DaggerMyProductComponent")

            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }

    @Test
    fun `lazy condition evaluation`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            object Features {
                val notReached: Boolean get() = throw AssertionError("Not reached")
                val disabled: Boolean get() = false
            }

            @AllConditions([
                Condition(Features::class, condition = "disabled"),
                Condition(Features::class, condition = "notReached"),
            ])
            annotation class A
            
            @Conditional([A::class])
            class ClassA @Inject constructor()

            @Component
            interface MyComponent { val a: Optional<ClassA> }

            fun test() {
                val c: MyComponent = DaggerMyComponent.create() 
                assert(!c.a.isPresent)
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }
}