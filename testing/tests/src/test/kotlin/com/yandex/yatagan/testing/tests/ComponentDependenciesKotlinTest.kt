/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.testing.tests

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
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Binds
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module            

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

        compileRunAndValidate()
    }

    @Test
    fun `plain interfaces are allowed as dependencies`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Component
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

        compileRunAndValidate()
    }


    @Test
    fun `component dependencies - dependency component instance is available for inject`() {
        givenKotlinSource("test.TestCase", """
            import javax.inject.Provider
            import javax.inject.Scope
            import javax.inject.Singleton
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Lazy

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

        compileRunAndValidate()
    }
}