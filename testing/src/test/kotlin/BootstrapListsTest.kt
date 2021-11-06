package com.yandex.daggerlite.testing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class BootstrapListsTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `basic test`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.BootstrapInterface
            import com.yandex.daggerlite.BootstrapList
            
            @BootstrapInterface
            interface Create
            
            @Singleton class ClassA @Inject constructor (b: ClassB) : Create
            @Singleton class ClassB @Inject constructor() : Create
            @Singleton class ClassC @Inject constructor (c: ClassA) : Create
            
            @Module(bootstrap = [
                ClassA::class,
                ClassB::class,
                ClassC::class,
            ])
            interface MyModule
            
            @Singleton @Component(modules = [MyModule::class])
            interface TestComponent {
                @BootstrapList
                fun bootstrap(): List<Create>
                @BootstrapList
                fun bootstrapLater(): Provider<List<Create>>
            }
        """.trimIndent())

        compilesSuccessfully {
            withNoWarnings()
            generatesJavaSources("test.DaggerTestComponent")
        }
    }
}
