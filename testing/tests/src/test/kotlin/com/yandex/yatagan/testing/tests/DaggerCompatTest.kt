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

import com.yandex.yatagan.generated.DaggerApiClasspath
import com.yandex.yatagan.generated.DaggerProcessorClasspath
import com.yandex.yatagan.processor.common.BooleanOption
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
                    apiClasspath = DaggerApiClasspath,
                )
            },
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "KSP"
                override fun get() = KspCompileTestDriver(
                    apiClasspath = DaggerApiClasspath,
                )
            },
            object : Provider<CompileTestDriverBase> {
                override fun toString() = "JAP(dagger-for-reference)"
                override fun get() = JapCompileTestDriver(
                    apiClasspath = DaggerApiClasspath,
                    customProcessorClasspath = DaggerProcessorClasspath,
                    checkGoldenOutput = false,
                )
            },
        )
    }

    @Before
    fun setUp() {
        givenOption(BooleanOption.DaggerCompatibilityMode, true)
    }

    @Test
    fun `basic test`() {
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
}