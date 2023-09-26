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

package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.Yatagan
import com.yandex.yatagan.codegen.poetry.java.PoetryJava
import com.yandex.yatagan.codegen.poetry.kotlin.PoetryKotlin
import com.yandex.yatagan.core.graph.BindingGraph

class ComponentGeneratorFacade(
    graph: BindingGraph,
    maxSlotsPerSwitch: Int,
    enableThreadChecks: Boolean,
    enableProvisionNullChecks: Boolean,
    generateKotlinCode: Boolean,
) {
    private val component = Yatagan.builder(GeneratorComponent.Factory::class.java).create(
        poetry = if (generateKotlinCode) PoetryKotlin() else PoetryJava(),
        graph = graph,
        options = ComponentGenerator.Options(
            maxSlotsPerSwitch = maxSlotsPerSwitch,
            enableProvisionNullChecks = enableProvisionNullChecks,
            enableThreadChecks = enableThreadChecks,
        ),
    )

    val targetPackageName: String
        get() = component.implementationClassName.packageName

    val targetClassName: String
        get() = component.implementationClassName.simpleNames.single()

    fun generateTo(out: Appendable) {
        component.generator.generateRoot(out)
    }
}
