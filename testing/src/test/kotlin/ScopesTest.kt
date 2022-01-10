package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class ScopesTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `singleton implicit binding is cached`() {
        givenKotlinSource(
            "test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component

           @Singleton 
           class ClassA @Inject constructor()

           @Component @Singleton
           interface TestComponent {
              fun getA(): ClassA
           }

           fun test() {
              val component = DaggerTestComponent.create()
              assert(component.getA() === component.getA())
           }
        """.trimIndent()
        )

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }

    @Test
    fun `unscoped implicit binding is not cached`() {
        givenKotlinSource(
            "test.TestCase", """
            import javax.inject.Inject
            import javax.inject.Singleton
            import com.yandex.daggerlite.Component
            
            class ClassA @Inject constructor()
            
            @Component @Singleton
            interface TestComponent {
            fun getA(): ClassA
            }
            
            fun test() {
            val component = DaggerTestComponent.create()
            assert(component.getA() !== component.getA())
            }
        """.trimIndent()
        )

        compilesSuccessfully {
            withNoWarnings()
            inspectGeneratedClass("test.TestCaseKt") { testCase ->
                testCase["test"](null)
            }
        }
    }
}