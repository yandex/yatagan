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

import com.yandex.yatagan.testing.source_set.SourceSet
import org.junit.Rule
import org.junit.Test

class UnresolvedTypeTest {
    @get:Rule
    val testNameRule = TestNameRule()

    private val sources = SourceSet {
        givenKotlinSource("test.TestCase", """
            import com.yandex.yatagan.*
            import javax.inject.*

            import com.example.UnresolvedEee
            import com.example.UnresolvedProvision
            import com.example.UnresolvedDep
            import com.example.UnresolvedInjectee
            import com.example.UnresolvedEp

            class Goo @Inject constructor(eee: UnresolvedEee)
            class Foo @Inject constructor(goo: Goo)

            @Module
            object TestModule {
                  @Provides
                  fun provide(foo: UnresolvedDep): UnresolvedProvision
            }

            @Component(modules = [TestModule::class])
            interface TestComponent<T> {
                val typeVar: T
                  
                val ep: UnresolvedEp
                val epList: List<UnresolvedEp>
                val foo: Foo
                
                fun inject(a: UnresolvedInjectee)
            }
        """.trimIndent())
    }

    @Test
    fun `unresolved types (KSP)`() = with(KspCompileTestDriver(useK2 = false)) {
        baseSetUp()
        testNameRule.assignFrom(this@UnresolvedTypeTest.testNameRule)

        // KSP1 still has some cases for error-types not covered.
        includeFromSourceSet(sources)
        compileRunAndValidate()
    }

    @Test
    fun `unresolved types (KSP2)`() = with(KspCompileTestDriver(useK2 = true)) {
        baseSetUp()
        testNameRule.assignFrom(this@UnresolvedTypeTest.testNameRule)

        includeFromSourceSet(sources)
        compileRunAndValidate()
    }

    @Test
    fun `unresolved types (JAP)`() = with(JapCompileTestDriver()) {
        baseSetUp()
        testNameRule.assignFrom(this@UnresolvedTypeTest.testNameRule)

        includeFromSourceSet(sources)
        compileRunAndValidate()
    }
}