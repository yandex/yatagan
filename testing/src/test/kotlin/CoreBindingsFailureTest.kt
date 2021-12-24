package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class CoreBindingsFailureTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }


    @Test
    fun `missing dependency test`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.Component
            import com.yandex.daggerlite.Lazy
            import javax.inject.Inject
            
            class Foo @Inject constructor(obj: Any, foo: Lazy<Foo2>)
            class Foo2 @Inject constructor(obj: Any)
            class ToInject {
                @set:Inject
                var obj: Any
            }
            
            @Component
            interface TestComponent {
                val foo: Foo
                val foo2: Foo2
                val hello: Any
                val bye: Any
                fun inject(i: ToInject)
            }
        """.trimIndent())

        failsToCompile {
            withError("Missing binding for java.lang.Object, no known way to create it") { notes ->
                assertEquals(expected = """
                    Encountered in:
                        test.TestComponent ⟶ [entry-point] getFoo ⟶ @Inject test.Foo ⟶ [missing: java.lang.Object]
                        test.TestComponent ⟶ [entry-point] getFoo ⟶ @Inject test.Foo ⟶ @Inject test.Foo2 ⟶ [missing: java.lang.Object]
                        test.TestComponent ⟶ [entry-point] getHello ⟶ [missing: java.lang.Object]
                        test.TestComponent ⟶ [entry-point] getBye ⟶ [missing: java.lang.Object]
                        test.TestComponent ⟶ [injector-fun] inject ⟶ [member-to-inject] setObj ⟶ [missing: java.lang.Object]
                    """.trimIndent(), actual = notes)
            }
        }
    }
}