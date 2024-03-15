package com.yandex.yatagan.testing.tests

import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class InstrumentationPluginsTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers(
            includeRt = false,  // TODO: Include RT once implemented
        )
    }

    private val hooks = SourceSet {
        givenJavaSource("test.InstrumentedHooks", """
            public class InstrumentedHooks {
                public static void log(String message) {
                    System.out.println(message);
                }
            }
        """.trimIndent())
        givenKotlinSource("test.InstrumentedHooks", """
            class HooksClass {
                companion object {
                    fun log(message: String) = println(message)
                }
            }
            object HooksObject {
                fun beginTrack(): Long = 0
                fun endTrack(component: Any, ms: Long) = println("Tracked component instance: " + component.javaClass)
            }
        """.trimIndent())
    }

    @Test
    fun `instrument empty component's constructor`() {
        includeFromSourceSet(hooks)

        givenPlugins(SourceSet {
            givenKotlinSource("test.TestPlugin", """
            import com.yandex.yatagan.core.graph.*
            import com.yandex.yatagan.instrumentation.*
            import com.yandex.yatagan.instrumentation.Expression.*
            import com.yandex.yatagan.instrumentation.Statement.*
            import com.yandex.yatagan.instrumentation.spi.*
            import com.yandex.yatagan.lang.Annotation
            import com.yandex.yatagan.lang.*
            class TestPlugin : InstrumentationPlugin {
                override fun shouldInstrument(graph: BindingGraph) = graph.model.type.toString().endsWith("Foo")
                override fun instrumentComponentCreation(graph: BindingGraph, delegate: InstrumentableAfter) {
                    val logMethods = buildList {
                        add(method("InstrumentedHooks", "log"))
                        add(LangModelFactory.getTypeDeclaration("test", "HooksClass")!!
                            .defaultCompanionObjectDeclaration!!.methods.first { it.name == "log" })
                    }
            
                    val message = graph.toString(null).toString() + " was created"
                    for (method in logMethods) {
                        delegate.after += Evaluate(
                            MethodCall(method, arguments = listOf(Literal.String(message)))
                        )
                    }
                    delegate.after.add(0, Assignment(
                        "timeMs",
                        MethodCall(method("HooksObject", "beginTrack")),
                    ))
                    if (graph.scopes.isNotEmpty()) {
                        delegate.after += Evaluate(
                            MethodCall(
                                method("ClassA", "onCreate"),
                                receiver = ResolveInstance(type = type("ClassA").asType()),
                            )
                        )
                    } else {
                        val declaration = LangModelFactory.getTypeDeclaration("javax.inject", "Named")!!
                                .asAnnotationDeclaration()
                        val qualifier = createComplexQualifier()
                        delegate.after += Evaluate(
                            MethodCall(
                                method("ApiB", "onCreate"),
                                receiver = ResolveInstance(type("ApiB").asType(), qualifier),
                                arguments = listOf(Literal.Char('W')),
                            )
                        )
                    }
                    delegate.after += Evaluate(
                        MethodCall(method("HooksObject", "endTrack"), arguments = listOf(
                            ReadValue(InstrumentableAfter.INSTANCE_VALUE_NAME),
                            ReadValue("timeMs"),
                        ))
                    )
                }
                private fun method(name: String, method: String): Method {
                    return type(name).methods.first { it.name == method }
                }
                private fun type(name: String): TypeDeclaration {
                    return LangModelFactory.getTypeDeclaration("test", name)!!
                }
                private fun createComplexQualifier(): Annotation = with(LangModelFactory) {
                    val nested = getTypeDeclaration("javax.inject", "Named")!!
                    getAnnotation(
                        type("ComplexQualifier").asAnnotationDeclaration(),
                    ) {
                        with(annotationValueFactory) {
                            when(it.name) {
                                // TODO: What if wrong integer type?
                                "value" -> valueOf(228.toShort())
                                "number" -> valueOf((-22).toByte())
                                "name" -> valueOf("hello world")
                                "arrayString" -> valueOf(listOf(
                                    valueOf("hello"),
                                    valueOf("world"),
                                ))
                                "arrayInt" -> valueOf(listOf(
                                    valueOf(1), valueOf(2), valueOf(3),
                                ))
                                "arrayChar" -> valueOf(listOf(
                                    valueOf('A'), valueOf('B'), valueOf('C'),
                                ))
                                "nested" -> valueOf(getAnnotation(
                                    nested.asAnnotationDeclaration()
                                ) { valueOf("nested-named") })
                                "arrayNested" -> valueOf(listOf(
                                    valueOf(getAnnotation(
                                        nested.asAnnotationDeclaration()
                                    ) { valueOf("array-nested") }),
                                ))
                                "enumValue" -> valueOf(type("MyEnum").asType(), "Red")
                                else -> throw AssertionError()
                            }
                        }
                    }
                }
            }
            """.trimIndent())
        }, mapOf("com.yandex.yatagan.instrumentation.spi.InstrumentationPlugin" to listOf("test.TestPlugin")))

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            @Component(modules = [MyModule::class]) interface MyComponentFoo {/*empty*/}
            @Component interface MyComponentBar {/*empty*/}
            @Singleton @Component interface MyComponent2Foo {/*empty*/}
            
            @Singleton class ClassA @Inject constructor() {
                fun onCreate() { println("onCreate in ClassA") }
            }
            
            enum class MyEnum { Red, Black }
            @Qualifier
            annotation class ComplexQualifier(
                val value: Short,
                val number: Byte,
                val name: String,
                val arrayString: Array<String>,
                val arrayInt: IntArray,
                val arrayChar: CharArray = ['A', 'B', 'C'],
                val nested: Named,
                val arrayNested: Array<Named>,
                val enumValue: MyEnum,
            )
            
            interface ApiB { fun onCreate(c: Char) { println("onCreate in ApiB: " + c) } }
            @Module class MyModule {
                @Provides
                @ComplexQualifier(
                    228,
                    number = -22,
                    name = "hello world",
                    arrayString = ["hello", "world"],
                    arrayInt = [1,2,3],
                    nested = Named("nested-named"),
                    arrayChar = ['A', 'B', 'C'],
                    arrayNested = [Named("array-nested")],
                    enumValue = MyEnum.Red,
                )
                fun provideApiB(): ApiB = object : ApiB {}
            }
            
            fun test() {
                Yatagan.create(MyComponentFoo::class.java)
                Yatagan.create(MyComponent2Foo::class.java)
                Yatagan.create(MyComponentBar::class.java)
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}