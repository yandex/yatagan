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
            @com.yandex.yatagan.ConditionExpression(value = "sEnabled", imports = MyFeature.class)
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
            @com.yandex.yatagan.Conditional(MyFeature.class)
            public class MyClassB implements Api { @javax.inject.Inject public MyClassB() {} } 
        """.trimIndent())
        givenKotlinSource("test.MyClassD", """
            class MyClassD @javax.inject.Inject internal constructor()
        """.trimIndent())
        givenJavaSource("test.MyModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            import com.yandex.yatagan.Binds;
            import javax.inject.Named;
            
            @Module /*package-private*/ abstract class MyModule {
                /*all is package-private*/
                abstract @Binds @Named("alias") Api bindApi(MyClassA i);
                abstract @Binds @Named("alt") Api bindApiAlt(MyClassB b, MyClassA a);
                abstract @Binds @Named("none") Api bindNoApi();
            }
        """.trimIndent())
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
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
                val component: TestComponent = Yatagan.create(TestComponent::class.java)
                component.api
                component.api2
                component.api3
            }
        """.trimIndent())

        compileRunAndValidate()
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
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            
            @Module public class PackagePrivateModule {
                @Provides
                static Object provideObject() { return new Object(); }
            }
        """.trimIndent())

        givenJavaSource("test.PackagePrivateProvidesModule", """
            import com.yandex.yatagan.Module;
            import com.yandex.yatagan.Provides;
            
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
            import com.yandex.yatagan.ConditionExpression;

            @ConditionExpression(value = "Flags::isEnabledA & Flags2::isEnabledB", imports = {Flags.class, Flags2.class})
            @interface Feature {}
        """.trimIndent())

        givenKotlinSource("test.UnderFeatureClass", """
            @com.yandex.yatagan.Conditional(Feature::class)
            class UnderFeatureClass @javax.inject.Inject constructor() 
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
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
            @com.yandex.yatagan.Component
            /*package-private*/ interface TestComponent2 {
                void inject2(Members2 m);
            }
        """.trimIndent())

        compileRunAndValidate()
    }

    @Test
    fun `assisted inject constructor must be publicly accessible`() {
        givenJavaSource("test.FooFactory", """
            import com.yandex.yatagan.AssistedFactory;
            import com.yandex.yatagan.Assisted;
            @AssistedFactory
            public interface FooFactory {
                Foo create();
            }
        """.trimIndent())
        givenJavaSource("test.BarFactory", """
            import com.yandex.yatagan.AssistedFactory;
            import com.yandex.yatagan.Assisted;
            @AssistedFactory
            public interface BarFactory {
                Bar create();
            }
        """.trimIndent())
        givenJavaSource("test.Foo", """
            import com.yandex.yatagan.AssistedInject;
            public class Foo { @AssistedInject Foo() {} }
        """.trimIndent())
        givenJavaSource("test.Bar", """
            import com.yandex.yatagan.AssistedInject;
            class Bar { @AssistedInject Bar() {} }
        """.trimIndent())

        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*

            @Component
            interface TestComponent {
                fun fooFactory(): FooFactory 
                fun barFactory(): BarFactory 
            }

            fun test() {
                val c = Yatagan.create(TestComponent::class.java)
                c.fooFactory().create()
                c.barFactory().create()
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}