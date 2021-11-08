package com.yandex.daggerlite.testing

import com.yandex.daggerlite.Lazy
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider
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
                @Component.Builder
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

                @Component.Builder
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

                @Component.Builder
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

                val fragmentFactory: MyFragmentComponent.Factory

                @Component.Builder
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

                @Component.Builder
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
                val activityComponent =
                    activityFactory.clz["create", String::class](activityFactory, /*activity_id*/"bar")

                with(activityComponent) {
                    val appManager = clz["getAppManager"](activityComponent)
                    val appManagerLazy = clz["getAppManagerLazy"](activityComponent) as Lazy<*>
                    val appManagerProvider = clz["getAppManagerProvider"](activityComponent) as Provider<*>
                    assertNotEquals(illegal = appManager, actual = appManagerLazy.get())
                    assertNotEquals(illegal = appManager, actual = appManagerProvider.get())
                    assertEquals(expected = appManagerLazy.get(), actual = appManagerLazy.get())
                }

                val fragmentFactory = activityComponent.clz["getFragmentFactory"](activityComponent)
                val fragmentComponent = fragmentFactory.clz["create"](fragmentFactory)

                with(fragmentComponent) {
                    val appManager = clz["getAppManager"](fragmentComponent)
                    val appManagerLazy = clz["getAppManagerLazy"](fragmentComponent) as Lazy<*>
                    val appManagerProvider = clz["getAppManagerProvider"](fragmentComponent) as Provider<*>
                    assertNotEquals(illegal = appManager, actual = appManagerLazy.get())
                    assertNotEquals(illegal = appManager, actual = appManagerProvider.get())
                    assertEquals(expected = appManagerLazy.get(), actual = appManagerLazy.get())
                }
            }
        }
    }

    @Test
    fun `subcomponents - one more advanced case`() {
        givenKotlinSource(
            "test.TestCase",
            """
            import kotlin.test.assertEquals

            @Scope annotation class ActivityScoped
             
            interface Theme
            
            class DefaultTheme @Inject constructor() : Theme
            
            class DarkTheme @Inject constructor(): Theme
            
            class RootClass

            @ActivityScoped
            class ActivityScopedClass @Inject constructor()

            class CameraSettings @Inject constructor(val theme: Theme)
            
            open class Activity
            
            class SettingsActivity : Activity()
            
            @Singleton class SingletonClass @Inject constructor()
             
            //////////////////////////////////////
            
            @Module(subcomponents = [MainActivityComponent::class, SettingsActivityComponent::class])
            interface ApplicationModule {
                companion object {
                    @Provides
                    fun rootClass(): RootClass = RootClass()
                }
            }
            
            @Module(subcomponents = [ProfileSettingsFragmentComponent::class, ProfileFragmentComponent::class])
            class SettingsActivityFragmentModule (private val settingsActivity: SettingsActivity) {
                @Provides fun activity(): Activity = settingsActivity
            }
            
            @Module
            object MainActivityModule {
                @Provides
                fun activity() = Activity()
            }
            
            @Module(subcomponents = [CameraFragmentComponent::class])
            interface CameraFragmentModule {
            }
            
            @Module(subcomponents = [CameraFragmentComponent::class, ProfileFragmentComponent::class])
            interface ProfileCameraModule
            
            @Module
            interface DefaultThemeModule {
                @Binds fun theme(i: DefaultTheme): Theme
            }
            @Module
            interface DarkThemeModule {
                @Binds fun theme(i: DarkTheme): Theme
            }
            
            //////////////////////////////////////
            
            @Singleton
            @Component(modules = [ApplicationModule::class])
            interface ApplicationComponent {    
                val mainActivity: MainActivityComponent.Factory
                val settingsActivity: SettingsActivityComponent.Factory
            }
            
            @ActivityScoped
            @Component(modules = [CameraFragmentModule::class, ProfileCameraModule::class, DefaultThemeModule::class, MainActivityModule::class],
                isRoot = false)
            interface MainActivityComponent {
                val cameraFragmentComponent: CameraFragmentComponent.Factory
                val profileFragmentComponent: ProfileFragmentComponent.Factory
                
                fun rootClass(): RootClass
            
                @Component.Builder
                interface Factory {
                    fun create(): MainActivityComponent
                }
            }
            
            @ActivityScoped
            @Component(modules = [DarkThemeModule::class, SettingsActivityFragmentModule::class], isRoot = false)
            interface SettingsActivityComponent {
                fun rootClass(): RootClass
                
                val profileFragmentComponent: ProfileFragmentComponent.Factory
                val profileSettingsFragmentComponent: ProfileSettingsFragmentComponent.Factory
                
                @Component.Builder
                interface Factory {
                    fun create(settingsActivityFragmentModule: SettingsActivityFragmentModule): SettingsActivityComponent
                }
            }
            
            @Component(isRoot = false)
            interface CameraFragmentComponent {
                fun cameraSettings(): CameraSettings
            
                @Component.Builder
                interface Factory {
                    fun create(): CameraFragmentComponent
                }
            }
            
            @Component(isRoot = false)
            interface ProfileFragmentComponent {
                fun activityScoped(): ActivityScopedClass
                fun activity(): Activity
                fun singleton(): SingletonClass
            
                @Component.Builder
                interface Factory {
                    fun create(): ProfileFragmentComponent
                }
            }
            
            @Component(isRoot = false)
            interface ProfileSettingsFragmentComponent {
                fun cameraSettings(): CameraSettings
            
                @Component.Builder
                interface Factory {
                    fun create(): ProfileSettingsFragmentComponent
                }
            }
            
            fun test() {
                val c = DaggerApplicationComponent()
                val settingsActivityFragmentModule = SettingsActivityFragmentModule(SettingsActivity())
                
                val mainActivityC = c.mainActivity.create()
                val settingsActivityC = c.settingsActivity.create(settingsActivityFragmentModule)
                
                val cameraFragmentC = mainActivityC.cameraFragmentComponent.create()
                val profileFragmentC = mainActivityC.profileFragmentComponent.create()
            
                val profileFragmentFromSettingsC = settingsActivityC.profileFragmentComponent.create()
                val profileSettingsFragmentC = settingsActivityC.profileSettingsFragmentComponent.create()
            
                assert(mainActivityC.rootClass() !== settingsActivityC.rootClass())

                assertEquals(profileFragmentC.activity()::class, Activity::class)
                assertEquals<Any>(profileFragmentFromSettingsC.activity()::class, SettingsActivity::class)

                assert(profileFragmentC.activityScoped() === profileFragmentC.activityScoped())
                assert(profileFragmentFromSettingsC.activityScoped() === profileFragmentFromSettingsC.activityScoped())
                assert(profileFragmentFromSettingsC.activityScoped() !== profileFragmentC.activityScoped())

                assert(profileFragmentC.singleton() === profileFragmentFromSettingsC.singleton())
                assertEquals(cameraFragmentC.cameraSettings().theme::class, DefaultTheme::class)
                assertEquals(profileSettingsFragmentC.cameraSettings().theme::class, DarkTheme::class)
            }
        """,
        )

        compilesSuccessfully {
            generatesJavaSources("test.DaggerApplicationComponent")
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") {
                it["test"](null)
            }
        }
    }
}
