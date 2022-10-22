package com.yandex.daggerlite.testing.tests

import com.yandex.daggerlite.testing.source_set.SourceSet
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class RetentionCheckTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers(
            includeRt = false,
        )
    }

    private lateinit var usages: SourceSet

    @Before
    fun setUp() {
        usages = SourceSet {
            givenKotlinSource("test.TestCase", """
                import com.yandex.daggerlite.*
                import javax.inject.*                

                @MyDefaultScope @MyBinaryScope @MyRuntimeScope
                class TestClass @Inject constructor()
    
                @Module
                object TestModule {
                    @[Provides MyBinaryQualifier] fun b1() = Any()
                    @[Provides MySourceQualifier] fun b2() = Any()
                    @[Provides MyDefaultQualifier] fun b3() = Any()
                    @[Provides MyRuntimeQualifier] fun b4() = Any()

                    @[Provides IntoMap TestMapKey(1)] fun intoMap() = Any()
                }
    
                @Component(modules = [TestModule::class])
                @MySourceScope @MyDefaultScope
                interface MyComponent {
                   @get:MyBinaryQualifier val b1: Any
                   @get:MySourceQualifier val b2: Any
                   @get:MyDefaultQualifier val b3: Any
                   @get:MyRuntimeQualifier val b4: Any
                   
                   val t: TestClass
                   val map: Map<Int, Any>
                }
            """.trimIndent())
        }
    }

    @Test
    fun `check annotations retention with kotlin origin`() {
        includeFromSourceSet(usages)

        givenKotlinSource("test.Annotations", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            @[Qualifier Retention(AnnotationRetention.BINARY)]
            annotation class MyBinaryQualifier

            @[Qualifier Retention(AnnotationRetention.SOURCE)]
            annotation class MySourceQualifier
            
            @[Qualifier Retention(AnnotationRetention.RUNTIME)]
            annotation class MyRuntimeQualifier
            
            @Qualifier
            annotation class MyDefaultQualifier

            @[Scope Retention(AnnotationRetention.BINARY)]
            annotation class MyBinaryScope

            @[Scope Retention(AnnotationRetention.SOURCE)]
            annotation class MySourceScope

            @[Scope Retention(AnnotationRetention.RUNTIME)]
            annotation class MyRuntimeScope
            
            @Scope
            annotation class MyDefaultScope

            @[IntoMap.Key Retention(AnnotationRetention.BINARY)]
            annotation class TestMapKey(val value: Int)
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `check annotations retention with java origin`() {
        includeFromSourceSet(usages)

        givenJavaSource("test.Annotations", """
            import com.yandex.daggerlite.IntoMap;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import javax.inject.Qualifier;
            import javax.inject.Scope;

            @Qualifier @Retention(RetentionPolicy.CLASS)
            @interface MyBinaryQualifier {}

            @Qualifier @Retention(RetentionPolicy.SOURCE)
            @interface MySourceQualifier {}
            
            @Qualifier @Retention(RetentionPolicy.RUNTIME)
            @interface MyRuntimeQualifier {}
            
            @Qualifier
            @interface MyDefaultQualifier {}

            @Scope @Retention(RetentionPolicy.CLASS)
            @interface MyBinaryScope {}

            @Scope @Retention(RetentionPolicy.SOURCE)
            @interface MySourceScope {}

            @Scope @Retention(RetentionPolicy.RUNTIME)
            @interface MyRuntimeScope {}
            
            @Scope
            @interface MyDefaultScope {}

            @IntoMap.Key
            @interface TestMapKey { int value(); }
        """.trimIndent())

        compileRunAndValidate()
    }
}