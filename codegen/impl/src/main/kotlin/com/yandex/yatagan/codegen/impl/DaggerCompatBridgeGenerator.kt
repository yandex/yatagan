/*
 * Copyright 2024 Yandex LLC
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
import com.squareup.javapoet.TypeSpec
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier

@Singleton
@Conditional(DaggerCompatEnabled::class)
internal class DaggerCompatBridgeGenerator @Inject constructor(
    private val componentImplName: ClassName,
    private val graph: BindingGraph,
    private val options: ComponentGenerator.Options,
) {
    val bridgeClassName: ClassName = graph.model.name.let {
        val name = when (it) {
            is ClassNameModel -> it
            is ParameterizedNameModel -> it.raw
            else -> throw AssertionError("Unexpected component name: $it")
        }
        // Name format is the same is dagger
        ClassName.get(name.packageName, "Dagger" + name.simpleNames.joinToString(separator = "_"))
    }

    fun generate(): TypeSpec = buildClass(bridgeClassName) {
        check(graph.isRoot)

        modifiers(Modifier.FINAL, Modifier.PUBLIC)
        annotation(Names.YataganGenerated)
        options.generatedAnnotationClassName?.let {
            annotation(it) { stringValue(value = "com.yandex.yatagan.codegen.impl.DaggerCompatBridgeGenerator") }
        }

        when(val factory = graph.model.factory) {
            null -> {
                method("create") {
                    modifiers(Modifier.PUBLIC, Modifier.STATIC)
                    returnType(graph.model.typeName())
                    +"return %T.autoBuilder().create()".formatCode(componentImplName)
                }
                // TODO: Maybe generate static dagger auto-builder?
            }
            else -> {
                method("builder") {
                    modifiers(Modifier.PUBLIC, Modifier.STATIC)
                    returnType(factory.typeName())
                    +"return %T.builder()".formatCode(componentImplName)
                }
                method("factory") {
                    modifiers(Modifier.PUBLIC, Modifier.STATIC)
                    returnType(factory.typeName())
                    +"return builder()"
                }
            }
        }
    }
}