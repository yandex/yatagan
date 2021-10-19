package com.yandex.daggerlite.compiler

import kotlin.test.Test

class ComponentHierarchyKotlinTest : CompileTestBase() {
    @Test
    fun `subcomponents - basic case`() {
        givenKotlinSource("test.TestCase", """
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

    @Test
    fun `subcomponents - advanced case`() {
        givenKotlinSource("test.TestCase", """
            @Scope annotation class ActivityScoped
            @Scope annotation class FragmentScoped

            interface MyApplicationManager
            class MyApplicationManagerImpl @Inject constructor(
                controller: MyApplicationController,
                @Named("app_id") id: String,
            ) : MyApplicationManager

            interface MyApplicationController
            @Singleton
            class MyApplicationControllerImpl @Inject constructor() : MyApplicationController
            
            @Module(subcomponents = [MyActivityComponent::class])
            interface ApplicationModule {
                @Binds
                fun appManager(i: MyApplicationManagerImpl): MyApplicationManager
                @Binds
                fun controller(i: MyApplicationControllerImpl): MyApplicationController
            }
            
            @Singleton
            @Component(modules = [ApplicationModule::class])
            interface MyApplicationComponent {
                @Component.Factory
                interface Factory {
                    fun create(
                        @BindsInstance @Named("app_id") appId: String,
                    ): MyApplicationComponent
                }
            }
    
            @Module(subcomponents = [MyFragmentComponent::class])
            interface MyActivityModule

            class MyActivityController @Inject constructor(
                appComponent: MyApplicationComponent,
                @Named("app_id") appId: Lazy<String>,
                @Named("activity_id") id: Provider<String>,
            )

            @ActivityScoped
            @Component(isRoot = false)
            interface MyActivityComponent {
                val appManager: MyApplicationManager
                val appManagerLazy: Lazy<MyApplicationManager>
                val appManagerProvider: Provider<MyApplicationManager>

                @Component.Factory
                interface Factory {
                    fun create(@BindsInstance @Named("activity_id") id: String): MyActivityComponent 
                }
            }

            @FragmentScoped
            class FragmentController @Inject constructor(
                val activityController: MyActivityController,
            )

            @FragmentScoped
            @Component(isRoot = false)
            interface MyFragmentComponent {
                val appManager: MyApplicationManager
                val appManagerLazy: Lazy<MyApplicationManager>
                val appManagerProvider: Provider<MyApplicationManager>
                
                val fragment: FragmentController

                @Component.Factory
                interface Factory {
                    fun create(): MyFragmentComponent 
                }
            }
        """)

        assertCompilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            withNoWarnings()
        }
    }
}