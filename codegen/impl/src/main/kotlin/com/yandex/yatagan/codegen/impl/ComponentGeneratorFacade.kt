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
import com.squareup.javapoet.TypeSpec
import com.yandex.yatagan.Yatagan
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.ThreadChecker
import com.yandex.yatagan.lang.langFactory

class ComponentGeneratorFacade(
    graph: BindingGraph,
    maxSlotsPerSwitch: Int,
    enableProvisionNullChecks: Boolean,
    sortMethodsForTesting: Boolean,
    enableDaggerCompatMode: Boolean,
    threadChecker: ThreadChecker,
) {
    interface GeneratedFile {
        val targetPackageName: String
        val targetClassName: String
        fun generateTo(out: Appendable)
    }

    private val langFactory = graph.model.type.ext.langFactory
    private val component = Yatagan.builder(GeneratorComponent.Factory::class.java).create(
        graph = graph,
        options = ComponentGenerator.Options(
            maxSlotsPerSwitch = maxSlotsPerSwitch,
            enableProvisionNullChecks = enableProvisionNullChecks,
            sortMethodsForTesting = sortMethodsForTesting,
            generatedAnnotationClassName = generatedAnnotationClassName(),
            enableDaggerCompatMode = enableDaggerCompatMode,
        ),
        threadChecker = threadChecker,
    )

    fun generate(): List<GeneratedFile> = buildList {
        add(GenerateFileImpl(
            className = component.implementationClassName,
            typeSpec = component.generator.generate(),
        ))
        component.daggerCompatGenerator.ifPresent {
            add(GenerateFileImpl(
                className = it.bridgeClassName,
                typeSpec = it.generate(),
            ))
        }
    }

    private fun generatedAnnotationClassName(): ClassName? {
        return Names.GeneratedJava8.takeIf { it.exists() } ?: Names.GeneratedJava9Plus.takeIf { it.exists() }
    }

    private fun ClassName.exists(): Boolean {
        return langFactory.getTypeDeclaration(packageName(), simpleName()) != null
    }

    private class GenerateFileImpl(
        val className: ClassName,
        val typeSpec: TypeSpec,
    ) : GeneratedFile {
        override val targetPackageName: String get() = className.packageName()
        override val targetClassName: String get() = className.simpleName()

        override fun generateTo(out: Appendable) {
            JavaFile.builder(targetPackageName, typeSpec)
                .build()
                .writeTo(out)
        }
    }
}
