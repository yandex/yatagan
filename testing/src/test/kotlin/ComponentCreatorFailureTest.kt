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
                message = Strings.Errors.`component must be an interface`(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.`missing component creator - non-root`(),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponent"),
                    listOf("test.RootComponent", "test.NotAComponent"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.`missing component creator - dependencies`(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.`missing component creator - modules`(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.SubComponent")),
                notes = listOf(Strings.Notes.`missing module instance`("test.MyModule"))
            ))
            withError(formatMessage(
                message = Strings.Errors.`declaration is not annotated with @Component`(),
                encounterPaths = listOf(listOf("test.RootComponent", "test.NotAComponent")),
            ))
            withError(formatMessage(
                message = Strings.Errors.`root component can not be a subcomponent`(),
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
                message = Strings.Errors.`non-void injector method return type`(),
                encounterPaths = listOf(listOf("test.MyComponent", "[injector-fun] inject"))
            ))
            withError(formatMessage(
                message = Strings.Errors.`invalid method in component`(
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
                message = Strings.Errors.`component creator must be an interface`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.`missing component dependency`(missing = "test.MyDependency"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.`missing module`(missing = "test.RequiresInstance"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Builder"),
                ),
            ))
            withError(formatMessage(
                message = Strings.Errors.`unneeded module`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Builder", "[param] create(.., module: test.Unknown, ..)"),
                )
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.`non-abstract dependency declaration`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "test.MyDependency"),
                )
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.`exposed dependency of a framework type`(
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
                message = Strings.Errors.`missing component creating method`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.`unneeded component dependency`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo", "[setter] setInt(int)"),
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.`invalid builder setter return type`("test.MyComponent.Foo"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.`invalid method in component creator`(
                    method = "test.MyComponent.Foo::create(): void"),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.`unneeded module`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo", "[setter] setModule(test.Unnecessary)"),
                )
            ))
            withError(formatMessage(
                message = Strings.Errors.`multiple component creators`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent"),
                ),
                notes = listOf(
                    Strings.Notes.`conflicting component creator declared`("test.MyComponent.Foo"),
                    Strings.Notes.`conflicting component creator declared`("test.MyComponent.Builder"),
                ),
            ))
            withWarning(formatMessage(
                message = Strings.Warnings.`@BindsInstance on builder method's parameter`(),
                encounterPaths = listOf(
                    listOf("test.MyComponent", "[builder] test.MyComponent.Foo", "[setter] setString(java.lang.String)"),
                )
            ))
            withNoMoreErrors()
            withNoMoreWarnings()
        }
    }
}