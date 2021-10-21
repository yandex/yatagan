package com.yandex.daggerlite.testing

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

                @Component.Factory
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

                @Component.Factory
                interface Factory {
                    fun create(app: MyApplicationComponent): MyActivityComponent 
                }
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            generatesJavaSources("test.DaggerMyActivityComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `component dependencies - dependency component instance is available for inject`() {
        givenKotlinSource("test.TestCase", """
            @Scope
            annotation class ActivityScoped

            @Singleton
            @Component
            interface MyApplicationComponent {
                @Component.Factory
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

                @Component.Factory
                interface Factory {
                    fun create(app: MyApplicationComponent): MyActivityComponent 
                }
            }
        """)

        compilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            generatesJavaSources("test.DaggerMyActivityComponent")
            withNoWarnings()
        }
    }
}