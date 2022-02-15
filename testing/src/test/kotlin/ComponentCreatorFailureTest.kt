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
                val obj: Any
            }
            interface NotAComponent
            @Component
            interface AnotherRootComponent
        """.trimIndent())

        failsToCompile {
            withError(formatMessage(
                message = Strings.Errors.nonInterfaceComponent(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.missingCreatorForNonRoot(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponent"),
                    listOf("test.RootComponent", "test.NotAComponent"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.missingCreatorForDependencies(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.missingCreatorForModules(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
                notes = listOf(Strings.Notes.missingModuleInstance("test.MyModule"))
            ))
            withError(formatMessage(
                message = Strings.Errors.nonComponent(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.NotAComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.rootAsChild(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.AnotherRootComponent")),
            ))
        }
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

        failsToCompile {
            withError(formatMessage(
                message = Strings.Errors.invalidInjectorReturn(),
                encounterPaths = listOf(listOf("test.MyComponent", "[injector-fun] inject"))
            ))
            withError(formatMessage(
                message = Strings.Errors.unknownMethodInComponent(
                    method = "test.MyComponent::misc(i: test.Injectee, extra: int): void"),
                encounterPaths = listOf(listOf("test.MyComponent"))
            ))
            withNoMoreWarnings()
            withNoMoreErrors()
        }
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
                val notGonnaBeUsed: Optional<Any>
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

        failsToCompile {
            withError(formatMessage(
                message = Strings.Errors.nonInterfaceCreator(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.missingComponentDependency(missing = "test.MyDependency"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.missingModule(missing = "test.RequiresInstance"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.extraModule(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Builder", "[param] create(.., module: test.Unknown, ..)"),
                )
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.nonAbstractDependency(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "test.MyDependency"),
                )
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.ignoredDependencyOfFrameworkType(
                    function = "test.MyDependency::getNotGonnaBeUsed(): com.yandex.daggerlite.Optional<java.lang.Object>"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "test.MyDependency"),
                )
            ))
            withNoMoreWarnings()
            withNoMoreErrors()
        }
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

        failsToCompile {
            withError(formatMessage(
                message = Strings.Errors.missingCreatingMethod(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.extraComponentDependency(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setInt(int)"),
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.invalidBuilderSetterReturn("test.MyComponent.Foo"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.unknownMethodInCreator(
                    method = "test.MyComponent.Foo::create(): void"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.extraModule(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setModule(test.Unnecessary)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.multipleCreators(),
                encounterPaths = listOf(
                    listOf("test.MyComponent"),
                ),
                notes = listOf(
                    Strings.Notes.conflictingCreator("test.MyComponent.Foo"),
                    Strings.Notes.conflictingCreator("test.MyComponent.Builder"),
                ),
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.ignoredBindsInstance(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[creator] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withNoMoreErrors()
            withNoMoreWarnings()
        }
    }
}