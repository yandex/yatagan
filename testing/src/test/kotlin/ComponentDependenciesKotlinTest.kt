package com.yandex.daggerlite.testing

import com.yandex.daggerlite.testing.support.CompileTestDriver
import com.yandex.daggerlite.testing.support.CompileTestDriverBase
import com.yandex.daggerlite.testing.support.compileTestDrivers
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ComponentDependenciesKotlinTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `component dependencies - basic case`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Provider
            import javax.inject.Scope
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Binds
            import com.yandex.daggerlite.Lazy
            import com.yandex.daggerlite.Module            

            @Scope
            annotation class ActivityScoped

            interface MyApplicationManager
            class MyApplicationManagerImpl @Inject constructor() : MyApplicationManager
            
            @Module
            interface ApplicationModule {
                @Binds
                fun appManager(i: MyApplicationManagerImpl): MyApplicationManager
            }
            
            @Singleton
            @Component(modules = [ApplicationModule::class])
            interface MyApplicationComponent {
                val appManager: MyApplicationManager

                @Component.Builder
                interface Factory {
                    fun create(): MyApplicationComponent 
                }
            }

            @ActivityScoped
            @Component(dependencies = [MyApplicationComponent::class])
            interface MyActivityComponent {
                val appManager: MyApplicationManager
                val appManagerLazy: Lazy<MyApplicationManager>
                val appManagerProvider: Provider<MyApplicationManager>

                @Component.Builder
                interface Factory {
                    fun create(app: MyApplicationComponent): MyActivityComponent 
                }
            }
        """.trimIndent())

        expectSuccessfulValidation()
    }

    @Test
    fun `plain interfaces are allowed as dependencies`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import javax.inject.Named

            class MyClass
    
            interface Dependencies {
                @get:Named val myClass: MyClass
            }
        
            @Component(dependencies = [Dependencies::class])
            interface MyComponent {
                @get:Named("") val myClass: MyClass
                
                @Component.Builder
                interface Builder {
                    fun create(dep: Dependencies): MyComponent
                }
            }
        """.trimIndent())

        expectSuccessfulValidation()
    }


    @Test
    fun `component dependencies - dependency component instance is available for inject`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Provider
            import javax.inject.Scope
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy

            @Scope
            annotation class ActivityScoped

            @Singleton
            @Component
            interface MyApplicationComponent {
                @Component.Builder
                interface Factory {
                    fun create(): MyApplicationComponent 
                }
            }

            @ActivityScoped
            @Component(dependencies = [MyApplicationComponent::class])
            interface MyActivityComponent {
                val app: MyApplicationComponent
                val appLazy: Lazy<MyApplicationComponent>
                val appProvider: Provider<MyApplicationComponent>

                @Component.Builder
                interface Factory {
                    fun create(app: MyApplicationComponent): MyActivityComponent 
                }
            }
        """.trimIndent())

        expectSuccessfulValidation()
    }
}