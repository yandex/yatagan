package com.yandex.daggerlite.testing

import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.formatMessage
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

        expectValidationResults(
            errorMessage(formatMessage(
                message = Strings.Errors.nonInterfaceComponent(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.missingCreatorForNonRoot(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.NotAComponent"),
                    listOf("test.RootComponent", "test.SubComponent"),
                ),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.missingCreatorForDependencies(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.missingCreatorForModules(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
                notes = listOf(Strings.Notes.missingModuleInstance("test.MyModule"))
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.nonComponent(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.NotAComponent")),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.rootAsChild(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.AnotherRootComponent")),
            )),
        )
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

        expectValidationResults(
            errorMessage(formatMessage(
                message = Strings.Errors.invalidInjectorReturn(),
                encounterPaths = listOf(listOf("test.MyComponent", "[injector-fun] inject"))
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.unknownMethodInComponent(
                    method = "test.MyComponent::misc(i: test.Injectee, extra: int): void"),
                encounterPaths = listOf(listOf("test.MyComponent"))
            )),
        )
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

        expectValidationResults(
            errorMessage(formatMessage(
                message = Strings.Errors.nonInterfaceCreator(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.missingComponentDependency(missing = "test.MyDependency"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.missingModule(missing = "test.RequiresInstance"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.extraModule(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder", "[param] create(.., module: test.Unknown, ..)"),
                )
            )),
            warningMessage(formatMessage(
                message = Strings.Warnings.nonAbstractDependency(),
                color = Strings.StringColor.Yellow,
                encounterPaths = listOf(
                    listOf("test.MyComponent", "test.MyDependency"),
                )
            )),
            warningMessage(formatMessage(
                message = Strings.Warnings.ignoredDependencyOfFrameworkType(
                    function = "test.MyDependency::getNotGonnaBeUsed(): com.yandex.daggerlite.Optional<java.lang.Object>"),
                color = Strings.StringColor.Yellow,
                encounterPaths = listOf(
                    listOf("test.MyComponent", "test.MyDependency"),
                )
            ))
        )
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

        expectValidationResults(
            errorMessage(formatMessage(
                message = Strings.Errors.missingCreatingMethod(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo"),
                )
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.extraComponentDependency(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setInt(int)"),
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.invalidBuilderSetterReturn("test.MyComponent.Foo"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.unknownMethodInCreator(
                    method = "test.MyComponent.Foo::create(): void"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo"),
                )
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.extraModule(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setModule(test.Unnecessary)"),
                )
            )),
            errorMessage(formatMessage(
                message = Strings.Errors.multipleCreators(),
                encounterPaths = listOf(
                    listOf("test.MyComponent"),
                ),
                notes = listOf(
                    Strings.Notes.conflictingCreator("test.MyComponent.Foo"),
                    Strings.Notes.conflictingCreator("test.MyComponent.Builder"),
                ),
            )),
            warningMessage(formatMessage(
                message = Strings.Warnings.ignoredBindsInstance(),
                color = Strings.StringColor.Yellow,
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            )),
        )
    }
}