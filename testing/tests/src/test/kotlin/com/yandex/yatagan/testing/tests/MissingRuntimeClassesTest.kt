/*
 * Copyright 2026 Yandex LLC
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
class MissingRuntimeClassesTest(
    driverProvider: Provider<CompileTestDriverBase>
) : CompileTestDriver by driverProvider.get() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers(
            includeKsp = false,
            includeJap = false,
        )
    }

    @Test
    fun `members injection skips supertype members that reference missing classes`() {
        givenClassMissingAtRuntime("test.Missing")
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            class Missing

            @Singleton
            class Dep @Inject constructor()

            open class Root {
                @Inject lateinit var rootField: Dep
                lateinit var rootMethodArg: Dep
                @Inject fun rootMethod(dep: Dep) { rootMethodArg = dep }
            }

            open class Base : Root() {
                @JvmField var missingField: Missing? = null
                fun missingMethod(missing: Missing) { missingField = missing }
            }

            class Child : Base() {
                @Inject lateinit var childField: Dep
            }

            @Singleton
            @Component
            interface TestComponent {
                fun dep(): Dep
                fun inject(child: Child)
            }

            fun test() {
                val component = Yatagan.create(TestComponent::class.java)
                val child = Child()
                component.inject(child)
                check(child.childField === component.dep())
                check(child.rootField === component.dep())
                check(child.rootMethodArg === component.dep())
            }
        """.trimIndent())

        compileRunAndValidate()
    }
}
