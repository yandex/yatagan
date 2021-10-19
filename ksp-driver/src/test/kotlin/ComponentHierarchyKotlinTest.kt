package com.yandex.daggerlite.compiler

import kotlin.test.Test

class ComponentHierarchyKotlinTest : CompileTestBase() {
    @Test
    fun `subcomponents - basic case`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Scope

            @Scope
            annotation class ActivityScoped

            interface MyApplicationManager
            class MyApplicationManagerImpl @Inject constructor() : MyApplicationManager
            
            @Module(subcomponents = [MyActivityComponent::class])
            interface ApplicationModule {
                @Binds
                fun appManager(i: MyApplicationManagerImpl): MyApplicationManager
            }
            
            @Singleton
            @Component(modules = [ApplicationModule::class])
            interface MyApplicationComponent {
                @Component.Factory
                interface Factory {
                    fun create(): MyApplicationComponent 
                }
            }

            @ActivityScoped
            @Component(isRoot = false)
            interface MyActivityComponent {
                val appManager: MyApplicationManager
                val appManagerLazy: Lazy<MyApplicationManager>
                val appManagerProvider: Provider<MyApplicationManager>

                @Component.Factory
                interface Factory {
                    fun create(): MyActivityComponent 
                }
            }
        """)

        assertCompilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            withNoWarnings()
        }
    }
}