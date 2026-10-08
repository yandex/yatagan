/*
 * Copyright 2024 Yandex LLC
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

import com.yandex.yatagan.generated.DaggerClasspath
import com.yandex.yatagan.processor.common.BooleanOption
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class DaggerCompatTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = listOf(
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "JAP"
                override fun get() = JapCompileTestDriver(
                    apiClasspath = DaggerClasspath.Api,
                )
            },
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "KSP"
                override fun get() = KspCompileTestDriver(
                    apiClasspath = DaggerClasspath.Api,
                )
            },
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "RT"
                override fun get() = DynamicCompileTestDriver(
                    apiClasspath = DaggerClasspath.ApiWithReflection,
                )
            },
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "JAP(dagger-for-reference)"
                override fun get() = JapCompileTestDriver(
                    apiClasspath = DaggerClasspath.Api,
                    customProcessorClasspath = DaggerClasspath.Processor,
                    checkGoldenOutput = false,
                    backendUnderTest = Backend.KaptDagger,
                )
            },
        )
    }

    @Before
    fun setUp() {
        givenOption(BooleanOption.DaggerCompatibilityMode, true)
    }

    @Test
    fun `pure dagger case - RT unsupported, dagger entry-points used`() {
        Assume.assumeFalse(backendUnderTest == Backend.Rt)

        givenKotlinSource("test.TestCase", """
            import dagger.*
            import dagger.assisted.*
            import dagger.multibindings.*
            import javax.inject.*
            import com.yandex.yatagan.Yatagan
            
            @Reusable class Foo @Inject constructor()         
            @Singleton class Bar @Inject constructor()
            @Scope annotation class MyScope
            
            @Module(subcomponents = [MySub2::class]) class MyModule {
                @Provides fun a(): Int = 0
            }

            interface MyDep {
                val foo: Foo
            }

            class AssistedClass @AssistedInject constructor(@Assisted("p1") i: Int, f: Foo)
            @AssistedFactory interface MyAssistedFactory {
                fun create(@Assisted("p1") i: Int): AssistedClass
            }
            
            @Singleton @Component
            interface MyDagger : MyDep {
                override val foo: Foo
                val bar: dagger.Lazy<Bar>
                
                fun createSub(mod: MyModule): MySub
            }

            class Top {
                @MyScope
                @Component(dependencies = [MyDep::class])
                interface MyDaggerNested {
                    val foo: Foo
                    fun getI(): Int
                    val af: MyAssistedFactory
                    
                    @Component.Factory
                    interface Factory {
                        fun create(@BindsInstance i: Int, dep: MyDep): MyDaggerNested
                    }
                }
            }
            
            @Subcomponent(modules = [MyModule::class]) interface MySub {
                val foo: Foo
                val a: Provider<Int>
                val s: MySub2.Builder
            }

            @Module object SubModule {
                @Provides @IntoSet @Named("list")
                fun intoSet(): Int = 3
                @Provides @ElementsIntoSet @Named("list")
                fun manyIntoSet(): Set<Int> = setOf(1, 2)

                @Provides @IntoMap @LongKey(123_456_789_000L)
                fun map1(): String = "big-num"

                @Provides @IntoMap @LongKey(2L)
                fun map2(): String = "small-num"
            }
            @Module(includes = [SubModule::class]) interface SubModuleI {
                @Binds fun collection(@Named("list") i: Set<Int>): Collection<Int>
            }

            @Subcomponent(modules = [SubModuleI::class])
            interface MySub2 {
                val i: Char
                @get:Named("list") val set: Set<Int>
                val col: Collection<Int>
                val map: Map<Long, String>
                
                @Subcomponent.Builder
                interface Builder {
                    @BindsInstance fun setChar(i: Char): Builder
                    fun build(): MySub2
                }
            }
            
            fun test() {
                DaggerMyDagger.create().run {
                    foo; bar.get()
                    createSub(MyModule()).run {
                      foo; a.get()
                      s.setChar('X').build().run {
                        i; assert(set == setOf(1, 2, 3)); map
                      }
                    }
                    DaggerTop_MyDaggerNested.factory().create(228, this).run {
                        foo; getI(); af.create(1)
                    }
                }
            }
        """.trimIndent())

        compileRunAndValidate()
    }


    @Test
    fun `mixed (duplicated) case - dagger compatible, Yatagan eps used for RT`() {
        givenKotlinSource("test.Classes", """
            import dagger.*
            import dagger.assisted.*
            import dagger.multibindings.*
            import javax.inject.*
            import com.yandex.yatagan.Component as YComponent
            import com.yandex.yatagan.AssistedFactory as YAssistedFactory
            import com.yandex.yatagan.AssistedInject as YAssistedInject
            import com.yandex.yatagan.Assisted as YAssisted
            import com.yandex.yatagan.Provides as YProvides
            import com.yandex.yatagan.Binds as YBinds
            import com.yandex.yatagan.BindsInstance as YBindsInstance
            import com.yandex.yatagan.Module as YModule
            import com.yandex.yatagan.IntoSet as YIntoSet
            import com.yandex.yatagan.IntoMap as YIntoMap
            
            // additional YReusable angers the vanilla dagger
            @Reusable class Foo @Inject constructor()

            @Singleton class Bar @Inject constructor()
            @Scope annotation class MyScope
            
            @Module(subcomponents = [MySub2::class])
            @YModule(subcomponents = [MySub2::class])
            class MyModule {
                @Provides fun a(): Int = 0
            }

            interface MyDep {
                val foo: Foo
            }

            class AssistedClass @AssistedInject @YAssistedInject constructor(
                    @Assisted("p1") @YAssisted("p1") i: Int, f: Foo)
            @AssistedFactory interface MyAssistedFactory {
                fun create(@Assisted("p1") @YAssisted("p1") i: Int): AssistedClass
            }
            
            @Singleton @Component @YComponent(multiThreadAccess = true)
            interface MyDagger : MyDep {
                override val foo: Foo
                val bar: dagger.Lazy<Bar>
                
                fun createSub(mod: MyModule): MySub
            }

            class Top {
                @MyScope
                @Component(dependencies = [MyDep::class])
                @YComponent(dependencies = [MyDep::class], multiThreadAccess = true)
                interface MyDaggerNested {
                    val foo: Foo
                    fun getI(): Int
                    val af: MyAssistedFactory
                    
                    @YComponent.Builder
                    @Component.Factory
                    interface Factory {
                        fun create(@BindsInstance @YBindsInstance i: Int, dep: MyDep): MyDaggerNested
                    }
                }
            }
            
            @Subcomponent(modules = [MyModule::class])
            @YComponent(isRoot = false, modules = [MyModule::class])
            interface MySub {
                val foo: Foo
                val a: Provider<Int>
                val s: MySub2.Builder
            }

            @Module @YModule object SubModule {
                @Provides @YProvides @IntoSet @YIntoSet @Named("list")
                fun intoSet(): Int = 3
                @Provides @YProvides @ElementsIntoSet @YIntoSet(flatten=true) @Named("list")
                fun manyIntoSet(): Set<Int> = setOf(1, 2)

                @Provides @YProvides @IntoMap @YIntoMap @LongKey(123_456_789_000L)
                fun map1(): String = "big-num"

                @Provides @YProvides @IntoMap @YIntoMap @LongKey(2L)
                fun map2(): String = "small-num"
            }
            @Module(includes = [SubModule::class])
            @YModule(includes = [SubModule::class])
            interface SubModuleI {
                @Binds @YBinds fun collection(@Named("list") i: Set<Int>): Collection<Int>
            }

            @Subcomponent(modules = [SubModuleI::class])
            @YComponent(isRoot = false, modules = [SubModuleI::class])
            interface MySub2 {
                val i: Char
                @get:Named("list") val set: Set<Int>
                val col: Collection<Int>
                val map: Map<Long, String>
                
                @Subcomponent.Builder
                @YComponent.Builder
                interface Builder {
                    @BindsInstance @YBindsInstance fun setChar(i: Char): Builder
                    fun build(): MySub2
                }
            }

            fun commonTest(md: MyDagger, n: Top.MyDaggerNested) {
                md.foo; md.bar.get()
                val sub = md.createSub(MyModule())
                sub.foo; sub.a.get()
                val sub2 = sub.s.setChar('X').build()
                sub2.i; assert(sub2.set == setOf(1, 2, 3)); sub2.map

                n.foo; n.getI(); n.af.create(1)
            }
            """)

        if (backendUnderTest == Backend.Rt) {
            givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan
            fun test() {
                val md = Yatagan.create(MyDagger::class.java)
                val n = Yatagan.builder(Top.MyDaggerNested.Factory::class.java).create(228, md)
                commonTest(md, n)
            }
            """.trimIndent())
        } else {
            givenKotlinSource("test.TestCase", """
            fun test() {
                val md = DaggerMyDagger.create()
                val n = DaggerTop_MyDaggerNested.factory().create(228, md)
                commonTest(md, n)
            }
            """.trimIndent())
        }

        compileRunAndValidate()
    }

    @Test
    fun `scrambled case - dagger incompatible`() {
        Assume.assumeFalse(backendUnderTest == Backend.KaptDagger)

        givenKotlinSource("test.Classes", """
            import dagger.*
            import dagger.assisted.*
            import dagger.multibindings.*
            import javax.inject.*
            import com.yandex.yatagan.Component as YComponent
            import com.yandex.yatagan.AssistedFactory as YAssistedFactory
            import com.yandex.yatagan.Assisted as YAssisted
            import com.yandex.yatagan.Reusable as YReusable
            import com.yandex.yatagan.Provides as YProvides
            import com.yandex.yatagan.Binds as YBinds
            import com.yandex.yatagan.BindsInstance as YBindsInstance
            import com.yandex.yatagan.Module as YModule
            import com.yandex.yatagan.IntoSet as YIntoSet
            import com.yandex.yatagan.IntoMap as YIntoMap
            import com.yandex.yatagan.Lazy as YLazy
            import com.yandex.yatagan.Optional
            
            @Reusable @YReusable class Foo @Inject constructor()         
            @Singleton class Bar @Inject constructor()
            @Scope annotation class MyScope
            
            @YModule(subcomponents = [MySub2::class]) class MyModule {
                @Provides fun a(): Int = 0
            }

            interface MyDep {
                val foo: Foo
            }

            class AssistedClass @AssistedInject constructor(@YAssisted("p1") k: Int,
                                                            @Assisted("p2") m: Int, f: Foo)
            @YAssistedFactory @AssistedFactory interface MyAssistedFactory {
                fun create(@Assisted("p1") i: Int, @YAssisted("p2") j: Int): AssistedClass
            }
            
            @Singleton @YComponent(multiThreadAccess = true)
            interface MyDagger : MyDep {
                override val foo: Foo
                val bar: dagger.Lazy<Bar>
                val ybar: YLazy<Bar>
                val yobar: Optional<Bar>
                val obar: Optional<dagger.Lazy<Foo>>
                
                fun createSub(mod: MyModule): MySub
            }

            class Top {
                @MyScope
                @Component(dependencies = [MyDep::class])
                @YComponent(dependencies = [MyDep::class], multiThreadAccess = true)
                interface MyDaggerNested {
                    val foo: Foo
                    fun getI(): Int
                    val af: MyAssistedFactory
                    
                    @YComponent.Builder
                    interface Factory {
                        fun create(@BindsInstance i: Int, dep: MyDep): MyDaggerNested
                    }
                }
            }
            
            @Subcomponent(modules = [MyModule::class]) interface MySub {
                val foo: Foo
                val a: Provider<Int>
                val s: MySub2.Builder
            }

            @Module @YModule object SubModule {
                @Provides @IntoSet @Named("list")
                fun intoSet(): Int = 3
                @Provides @ElementsIntoSet @YIntoSet(flatten=true) @Named("list")
                fun manyIntoSet(): Set<Int> = setOf(1, 2)

                @YProvides @YIntoMap @LongKey(123_456_789_000L)
                fun map1(): String = "big-num"

                @YProvides @Provides @IntoMap @LongKey(2L)
                fun map2(): String = "small-num"
            }
            @Module(includes = [SubModule::class]) interface SubModuleI {
                @Binds @YBinds fun collection(@Named("list") i: Set<Int>): Collection<Int>
            }

            @YComponent(isRoot = false, modules = [SubModuleI::class])
            interface MySub2 {
                val i: Char
                @get:Named("list") val set: Set<Int>
                val col: Collection<Int>
                val map: Map<Long, String>
                
                @Subcomponent.Builder
                interface Builder {
                    @BindsInstance fun setChar(i: Char): Builder
                    @YBindsInstance fun setShort(i: Short): Builder
                    fun build(@YBindsInstance a: Any, @BindsInstance f: Double): MySub2
                }
            }

            fun commonTest(md: MyDagger, n: Top.MyDaggerNested) {
                md.foo; md.bar.get()
                val sub = md.createSub(MyModule())
                sub.foo; sub.a.get()
                val sub2 = sub.s.setChar('X').setShort(0).build(Any(), 0.0)
                sub2.i; assert(sub2.set == setOf(1, 2, 3)); sub2.map

                n.foo; n.getI(); n.af.create(1, 2)
            }
            """)

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.Yatagan
            fun test() {
                val md = Yatagan.create(MyDagger::class.java)
                val n = Yatagan.builder(Top.MyDaggerNested.Factory::class.java).create(228, md)
                commonTest(md, n)
            }
            """.trimIndent()
        )

        compileRunAndValidate()
    }
}