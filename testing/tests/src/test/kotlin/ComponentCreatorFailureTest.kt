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
            import com.yandex.yatagan.*
            import javax.inject.*
            
            @Module(subcomponents = [SubComponent::class, NotAComponent::class, AnotherRootComponent::class])
            interface RootModule
            
            @Component(modules = [RootModule::class])
            interface RootComponent
            interface MyDependency
            @Module class MyModule(@get:Provides val obj: Any)
            @Component(isRoot = false, dependencies = [MyDependency::class], modules = [MyModule::class])
            abstract class SubComponent {
                abstract val obj: Any
            }
            interface NotAComponent
            @Component
            interface AnotherRootComponent
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid member-injector`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            interface Injectee
            
            @Component
            interface MyComponent {
                fun misc(i: Injectee, extra: Int)
                fun inject(i: Injectee): Int
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `missing entities in creator`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
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
                val notGonnaBeUsed: Optional<Any> = Optional.empty()
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

        compileRunAndValidate()
    }

    @Test
    fun `invalid component creator`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
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
            
            @Module
            class NoBindings
            
            @Module
            object ObjectModule
            
            @Component(modules = [ObjectModule::class, NoBindings::class])
            interface MyComponent {
                @Component.Builder
                interface Foo : FooBase {
                    fun setInt(i: Int)
                    @BindsInstance fun setLong(i: Long)
                    @BindsInstance fun setDouble(i: Double): Foo
                    fun setString(@BindsInstance i: String): String
                    fun setModule(m: Unnecessary)
                    fun setModule2(m: ObjectModule)
                    fun setModule3(m: NoBindings)
                    fun create()
                }
            
                @Component.Builder
                interface Builder {
                    fun create(): MyComponent
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid subcomponent inclusion`() {
        givenKotlinSource("test.TestSource", """
            import com.yandex.yatagan.*
            import javax.inject.*

            @Component(isRoot = false)
            interface SubComponent1 {
                @Component.Builder interface Builder { fun create(): SubComponent1 }
            }

            @Component(isRoot = false)
            interface SubComponent2 {
                @Component.Builder interface Builder { fun create(): SubComponent2 }
            }

            @Module(subcomponents = [SubComponent2::class])
            interface MyModule

            @Component(modules = [MyModule::class])
            interface RootComponent {
                val sub1: SubComponent1
                val sub2: SubComponent2
                val fsub: FeatureComponent
            }

            @Condition(Features::class, "isEnabled")
            annotation class Feature

            @Component(isRoot = false)
            @Conditional(Feature::class)
            interface FeatureComponent

            class Features @Inject constructor() {
                val isEnabled get() = false
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `invalid sub-component factory methods`() {
        givenKotlinSource("test.TestSource", """
            import com.yandex.yatagan.*
            import javax.inject.*

            interface MyDependencies

            class ConditionProvider @Inject constructor() {
                var isEnabled: Boolean = false
                @Condition(ConditionProvider::class, "isEnabled") annotation class IsEnabled
            }
            
            @Component
            interface RootComponent2 {
                fun sub1EP(): SubComponent1.Factory
            }

            @Conditional(ConditionProvider.IsEnabled::class)
            @Component(isRoot = false, dependencies = [MyDependencies::class])
            interface SubUnderFeature

            @Component
            interface RootComponent1 {
                fun sub1EP(): SubComponent1
                fun sub1FactoryMethod(dep: MyDependencies): SubComponent1

                fun sub2FactoryMethod1(foo: Any): SubComponent2
                fun sub2FactoryMethod2(dep: MyDependencies): SubComponent2
                
                fun createSubComponent3(@BindsInstance i: Int): SubComponent3
                
                fun unknown(): RootComponent2
                
                fun subUnderFeature(dep: MyDependencies): SubUnderFeature
            }

            @Component(isRoot=false, dependencies = [MyDependencies::class])
            interface SubComponent1 {
                @Component.Builder interface Factory { fun create(dep: MyDependencies): SubComponent1 }
            }

            @Component(isRoot=false, dependencies = [MyDependencies::class])
            interface SubComponent2

            @Component(isRoot=false)
            interface SubComponent3 {
                val i: Int
                fun createSubComponent3(@BindsInstance i: Int): SubComponent3
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}