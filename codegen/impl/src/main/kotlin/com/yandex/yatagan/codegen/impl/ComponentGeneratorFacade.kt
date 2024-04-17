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

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.yandex.yatagan.Yatagan
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.lang.langFactory

class ComponentGeneratorFacade(
    graph: BindingGraph,
    maxSlotsPerSwitch: Int,
    enableThreadChecks: Boolean,
    enableProvisionNullChecks: Boolean,
    sortMethodsForTesting: Boolean,
) {
    private val langFactory = graph.model.type.ext.langFactory
    private val component = Yatagan.builder(GeneratorComponent.Factory::class.java).create(
        graph = graph,
        options = ComponentGenerator.Options(
            maxSlotsPerSwitch = maxSlotsPerSwitch,
            enableProvisionNullChecks = enableProvisionNullChecks,
            enableThreadChecks = enableThreadChecks,
            sortMethodsForTesting = sortMethodsForTesting,
            generatedAnnotationClassName = generatedAnnotationClassName(),
        ),
    )

    val targetPackageName: String
        get() = component.implementationClassName.packageName()

    val targetClassName: String
        get() = component.implementationClassName.simpleName()

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, component.generator.generate())
            .build()
            .writeTo(out)
    }

    private fun generatedAnnotationClassName(): ClassName? {
        return Names.GeneratedJava8.takeIf { it.exists() } ?: Names.GeneratedJava9Plus.takeIf { it.exists() }
    }

    private fun ClassName.exists(): Boolean {
        return langFactory.getTypeDeclaration(packageName(), simpleName()) != null
    }
}
