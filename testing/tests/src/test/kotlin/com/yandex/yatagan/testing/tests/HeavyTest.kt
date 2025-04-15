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

import com.yandex.yatagan.testing.procedural.Distribution
import com.yandex.yatagan.testing.procedural.ExperimentalGenerationApi
import com.yandex.yatagan.testing.procedural.GenerationParams
import com.yandex.yatagan.testing.procedural.generate
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.inject.Provider

@RunWith(Parameterized::class)
@OptIn(ExperimentalGenerationApi::class)
class HeavyTest(
    driverProvider: Provider<CompileTestDriverBase>,
) : CompileTestDriver by driverProvider.get() {
    companion object {
        private val baseParams = GenerationParams(
            componentTreeMaxDepth = 6,
            totalGraphNodesCount = 300,
            bindings = Distribution.build {
                GenerationParams.BindingType.Alias exactly 0.1
                GenerationParams.BindingType.ComponentDependencyEntryPoint exactly 0.08
                GenerationParams.BindingType.ComponentDependency exactly 0.0
                GenerationParams.BindingType.Instance exactly 0.08
                theRestUniformly()
            },
            maxProvisionDependencies = 30,
            provisionDependencyKind = Distribution.build {
                GenerationParams.DependencyKind.Direct exactly 0.8
                theRestUniformly()
            },
            maxEntryPointsPerComponent = 15,
            maxMemberInjectorsPerComponent = 3,
            maxMembersPerInjectee = 10,
            totalRootCount = 3,
            maxChildrenPerComponent = 5,
            totalRootScopes = 1,
            maxChildrenPerScope = 5,
            seed = 1236473289L,
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = compileTestDrivers()
    }

    @Test
    fun `procedural - Tall & Thin`() {
        // TODO(#184): fix and enable test back
        assumeFalse(backendUnderTest == Backend.Ksp2)
        generate(
            params = baseParams,
            testMethodName = "test",
            output = this,
        )

        compileRunAndValidate()
    }

    @Test
    fun `procedural - Fat & Flat`() {
        // TODO(#184): fix and enable test back
        assumeFalse(backendUnderTest == Backend.Ksp2)
        generate(
            params = baseParams.copy(
                componentTreeMaxDepth = 1,
                totalGraphNodesCount = 600,
                totalRootCount = 2,
                maxEntryPointsPerComponent = 60,
            ),
            testMethodName = "test",
            output = this,
        )

        compileRunAndValidate()
    }
}