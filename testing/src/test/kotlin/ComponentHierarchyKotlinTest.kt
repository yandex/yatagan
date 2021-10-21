package com.yandex.daggerlite.testing

import dagger.Lazy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Method
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(Parameterized::class)
class ComponentHierarchyKotlinTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `subcomponents - basic case`() {
        givenKotlinSource(
            "test.TestCase", """
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
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            withNoWarnings()
        }
    }

    @Test
    fun `subcomponents - advanced case`() {
        givenKotlinSource(
            "test.TestCase", """
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
                val activityFactory: MyActivityComponent.Factory

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
            @Component(isRoot = false, modules = [MyActivityModule::class])
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
        """
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerMyApplicationComponent")
            withNoWarnings()
            inspectGeneratedClass("test.DaggerMyApplicationComponent") {
                val factory = it["factory"](null)
                val appComponent = factory.clz["create", String::class](factory, /*app_id*/"foo")
                val activityFactory = appComponent.clz["getActivityFactory"](appComponent)
                val activityComponent = activityFactory.clz["create", String::class](activityFactory, /*activity_id*/"bar")

                with(activityComponent) {
                    val appManager = clz["getAppManager"](activityComponent)
                    val appManagerLazy = clz["getAppManagerLazy"](activityComponent) as Lazy<*>
                    val appManagerProvider = clz["getAppManagerProvider"](activityComponent) as Provider<*>
                    assertNotEquals(illegal = appManager, actual = appManagerLazy.get())
                    assertNotEquals(illegal = appManager, actual = appManagerProvider.get())
                    assertEquals(expected = appManagerLazy.get(), actual = appManagerLazy.get())
                }
            }
        }
    }
}

private operator fun Class<*>.get(name: String, vararg params: KClass<*>): Method {
    return getDeclaredMethod(name, *params.map { it.java }.toTypedArray()).also {
        it.isAccessible = true
    }
}

private val Any.clz get() = javaClass