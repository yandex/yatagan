package com.yandex.daggerlite.testing

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
                    listOf("test.TestComponent",
                        "[entry-point] getFoo",
                        "@Inject test.Foo",
                        "[missing: java.lang.Object]"),
                    listOf("test.TestComponent",
                        "[entry-point] getFoo",
                        "@Inject test.Foo",
                        "@Inject test.Foo2",
                        "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[entry-point] getHello", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent", "[entry-point] getBye", "[missing: java.lang.Object]"),
                    listOf("test.TestComponent",
                        "[injector-fun] inject",
                        "[member-to-inject] setObj",
                        "[missing: java.lang.Object]"),
                )
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
                message = Errors.`no matching scope for binding`(binding = "@Inject test.Foo",
                    scope = "@javax.inject.Singleton"),
                encounterPaths = listOf(
                    listOf("test.RootComponent", "test.SubComponent", "[entry-point] getFooForSub", "[missing: test.Foo]"),
                    listOf("test.RootComponent", "[entry-point] getFooForRoot", "[missing: test.Foo]"),
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
                )
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
}