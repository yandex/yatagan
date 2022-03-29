package com.yandex.daggerlite.testing

import com.yandex.daggerlite.testing.support.CompileTestDriver
import com.yandex.daggerlite.testing.support.CompileTestDriverBase
import com.yandex.daggerlite.testing.support.compileTestDrivers
import com.yandex.daggerlite.testing.support.errorMessage
import com.yandex.daggerlite.validation.impl.Strings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
class AccessControlTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    // region backends setup
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }
    // endregion

    @Test
    fun `@Binds declarations can be package-private`() {
        givenJavaSource("test.MyFeature", """
            @com.yandex.daggerlite.Condition(value = MyFeature.class, condition = "sEnabled")
            public @interface MyFeature {
                boolean sEnabled = false;
            }
        """.trimIndent())
        givenJavaSource("test.Api", """
            public interface Api {}
        """.trimIndent())
        givenJavaSource("test.MyClassA", """
            public class MyClassA implements Api { @javax.inject.Inject public MyClassA() {} } 
        """.trimIndent())
        givenJavaSource("test.MyClassB", """
            @com.yandex.daggerlite.Conditional(MyFeature.class)
            public class MyClassB implements Api { @javax.inject.Inject public MyClassB() {} } 
        """.trimIndent())
        givenKotlinSource("test.MyClassD", """
            class MyClassD @javax.inject.Inject internal constructor()
        """.trimIndent())
        givenJavaSource("test.MyModule", """
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Provides;
            import com.yandex.daggerlite.Binds;
            import javax.inject.Named;
            
            @Module /*package-private*/ abstract class MyModule {
                /*all is package-private*/
                abstract @Binds @Named("alias") Api bindApi(MyClassA i);
                abstract @Binds @Named("alt") Api bindApiAlt(MyClassB b, MyClassA a);
                abstract @Binds @Named("none") Api bindNoApi();
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*
            
            @Component(modules = [MyModule::class])
            interface TestComponent {
                @get:Named("alias")
                val api: Api
                
                @get:Named("alt")
                val api2: Api
                
                @get:Named("none")
                val api3: Optional<Api>
                
                val d: MyClassD
            }
            
            fun test() {
                // must not crash on RT
                val component: TestComponent = Dagger.create(TestComponent::class.java)
                component.api
                component.api2
                component.api3
            }
        """.trimIndent())

        expectSuccessfulValidation()
    }

    @Test
    fun `inject-constructors, provisions and conditions must be publicly accessible`() {
        givenJavaSource("test.WithPackagePrivateInject", """
            public class WithPackagePrivateInject {
                /*package-private*/ @javax.inject.Inject
                WithPackagePrivateInject() {}
            }
        """.trimIndent())
        givenJavaSource("test.WithProtectedInject", """
            public class WithProtectedInject {
                protected @javax.inject.Inject
                WithProtectedInject() {}
            }
        """.trimIndent())
        givenKotlinSource("test.WithProtectedInjectKotlin", """
            open class WithProtectedInjectKotlin @javax.inject.Inject protected constructor()
        """.trimIndent())

        givenJavaSource("test.PackagePrivateModule", """
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Provides;
            
            @Module public class PackagePrivateModule {
                @Provides
                static Object provideObject() { return new Object(); }
            }
        """.trimIndent())

        givenJavaSource("test.PackagePrivateProvidesModule", """
            import com.yandex.daggerlite.Module;
            import com.yandex.daggerlite.Provides;
            
            @Module class PackagePrivateProvidesModule {
                @Provides
                public static String provideString() { return ""; }
            }
        """.trimIndent())

        givenJavaSource("test.Members", """
            import javax.inject.Inject;
            
            public class Members {
                @Inject private WithPackagePrivateInject m1;  // not reported - private is invisible
                @Inject /*package-private*/ WithProtectedInject m2;
                @Inject protected Object m3;
                @Inject /*package-private*/ void setObject(Object a) {}
                @Inject private void setWithProtectedInject(WithProtectedInject a) {}  // not reported - private is invisible
            }
        """.trimIndent())
        givenJavaSource("test.Members2", """
            /*package-private*/ class Members2 {}
        """.trimIndent())
        givenJavaSource("test.Flags", """
            public class Flags {
                /*package-private*/ static boolean isEnabledA = false;
            }
        """.trimIndent())
        givenJavaSource("test.Flags2", """
            /*package-private*/ class Flags2 {
                public static boolean isEnabledB = false;
            }
        """.trimIndent())
        givenJavaSource("test.Feature", """
            import com.yandex.daggerlite.Condition;

            @Condition(value = Flags.class, condition = "isEnabledA")
            @Condition(value = Flags2.class, condition = "isEnabledB")
            @interface Feature {}
        """.trimIndent())

        givenKotlinSource("test.UnderFeatureClass", """
            @com.yandex.daggerlite.Conditional([Feature::class])
            class UnderFeatureClass @javax.inject.Inject constructor() 
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.daggerlite.*
            import javax.inject.*

            @Component(modules = [
                PackagePrivateModule::class,
                PackagePrivateProvidesModule::class,
            ])
            interface TestComponent {
                val o1: WithPackagePrivateInject
                val o2: WithProtectedInject
                val o3: Any
                val o4: Optional<UnderFeatureClass>
                
                fun inject(m: Members)
            }
        """.trimIndent())
        givenJavaSource("test.TestComponent2", """
            @com.yandex.daggerlite.Component
            /*package-private*/ interface TestComponent2 {
                void inject2(Members2 m);
            }
        """.trimIndent())

        expectValidationResults(
            // @formatter:off
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessInjectConstructor(),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[entry-point] getO1", "@Inject test.WithPackagePrivateInject"),
                    listOf("test.TestComponent", "[injector-fun] inject", "[member-to-inject] m2", "@Inject test.WithProtectedInject"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForConditionClass(`class` = "test.Flags2"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[entry-point] getO4", "@Inject test.UnderFeatureClass", "[test.Flags.isEnabledA && test.Flags2.isEnabledB]", "test.Flags2.isEnabledB"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForConditionMember(member = "test.Flags::isEnabledA: boolean"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[entry-point] getO4", "@Inject test.UnderFeatureClass", "[test.Flags.isEnabledA && test.Flags2.isEnabledB]", "test.Flags.isEnabledA"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForMemberToInject(member = "test.Members::m2: test.WithProtectedInject"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[injector-fun] inject"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForMemberToInject(member = "test.Members::m3: java.lang.Object"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[injector-fun] inject"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForMemberToInject(member = "test.Members::setObject(a: java.lang.Object): void"),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "[injector-fun] inject"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForProvides(),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.PackagePrivateModule", "@Provides test.PackagePrivateModule::provideObject(): java.lang.Object"),
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForModuleClass(),
                encounterPaths = listOf(
                    listOf("test.TestComponent", "test.PackagePrivateProvidesModule")
                ),
            )),
            errorMessage(Strings.formatMessage(
                message = Strings.Errors.invalidAccessForMemberInject(),
                encounterPaths = listOf(
                    listOf("test.TestComponent2", "[injector-fun] inject2")
                ),
            )),
            // @formatter:on
        )
    }
}