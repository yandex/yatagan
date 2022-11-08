package com.yandex.yatagan.testing.tests

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
            import com.yandex.yatagan.*
            import javax.inject.*

           @Singleton 
           class ClassA @Inject constructor()

           @Component @Singleton
           interface TestComponent {
              fun getA(): ClassA
           }

           fun test() {
              val component = Yatagan.create(TestComponent::class.java)
              assert(component.getA() === component.getA())
           }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `unscoped implicit binding is not cached`() {
        givenKotlinSource(
            "test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*
            
            class ClassA @Inject constructor()
            
            @Component @Singleton
            interface TestComponent {
            fun getA(): ClassA
            }
            
            fun test() {
            val component = Yatagan.create(TestComponent::class.java)
            assert(component.getA() !== component.getA())
            }
        """.trimIndent()
        )

        compileRunAndValidate()
    }

    @Test
    fun `multi-threaded mode`() {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import java.util.concurrent.CountDownLatch
            import javax.inject.*
            import kotlin.concurrent.thread
            
            @Singleton
            class ClassA @Inject constructor()
            
            @Singleton
            class ClassB @Inject constructor(val a: ClassA)
            
            @Singleton
            class ClassC @Inject constructor(val a: ClassA)
            
            class ClassD @Inject constructor(val b: ClassB)
            class ClassE @Inject constructor(val b: ClassB)
             
            @Component(multiThreadAccess = true) @Singleton
            interface TestComponent {
                val a: ClassA
                val b: Lazy<ClassB>
                val c: Provider<ClassC>
                val d: Provider<ClassD>
                val e: Lazy<ClassE>
            }
            
            fun test() {
                val c: TestComponent = Yatagan.create(TestComponent::class.java)
                val threads = arrayListOf<Thread>()
                val cd = CountDownLatch(8)
                repeat(8) {
                    threads += thread {
                        cd.countDown()
                        cd.await()
                        repeat(100) {
                            assert(c.a === c.a && c.a === c.b.get().a &&
                                   c.a === c.c.get().a && c.a === c.d.get().b.a)
                            assert(c.b.get() === c.b.get())
                            assert(c.d.get() !== c.d.get())
                            assert(c.e.get() !== c.e.get())
                            val e = c.e
                            assert(e.get() === e.get())
                        }
                    }
                }
                threads.forEach(Thread::join)
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}