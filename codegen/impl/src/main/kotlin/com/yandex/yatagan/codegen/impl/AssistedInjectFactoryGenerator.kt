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
import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier

@Singleton
internal class AssistedInjectFactoryGenerator @Inject constructor(
    private val thisGraph: BindingGraph,
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    private val modelToImpl: Map<AssistedInjectFactoryModel, ClassName> by lazy {
        val classNamespace = Namespace()
        thisGraph.localAssistedInjectFactories.associateWith { model ->
            componentImplName.nestedClass(classNamespace.name(
                nameModel = model.name,
                suffix = "Impl",
                firstCapital = true,
            ))
        }
    }

    fun generateCreation(
        builder: ExpressionBuilder,
        binding: AssistedInjectFactoryBinding,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) {
        val localImplName = modelToImpl[binding.model]
        if (localImplName != null) {
            with(builder) {
                +"%L.new %T()".formatCode(
                    componentInstance(
                        inside = inside,
                        graph = thisGraph,
                        isInsideInnerClass = isInsideInnerClass,
                    ),
                    localImplName,
                )
            }
        } else {
            thisGraph.parent!![GeneratorComponent].assistedInjectFactoryGenerator
                .generateCreation(
                    builder = builder,
                    binding = binding,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
        }
    }

    override fun generate(builder: TypeSpecBuilder) {
        for ((model, implName) in modelToImpl) {
            builder.nestedType {
                buildClass(implName) {
                    modifiers(Modifier.PRIVATE, Modifier.FINAL)  // Non-static
                    implements(model.typeName())

                    model.factoryMethod?.let { factoryMethod ->
                        overrideMethod(factoryMethod) {
                            modifiers(Modifier.PUBLIC)
                            +buildExpression {
                                +"return new %T(".formatCode(
                                    model.assistedInjectConstructor?.constructee?.asType()?.typeName() ?: "#error")
                                val factoryParameters = factoryMethod.parameters.toList()
                                join(model.assistedConstructorParameters) { parameter ->
                                    when (parameter) {
                                        is AssistedInjectFactoryModel.Parameter.Assisted -> {
                                            val index = model.assistedFactoryParameters.indexOf(parameter)
                                            val name = if (index >= 0) factoryParameters[index].name else "#error"
                                            +"%N".formatCode(name)
                                        }
                                        is AssistedInjectFactoryModel.Parameter.Injected -> {
                                            val (node, kind) = parameter.dependency
                                            thisGraph.resolveBinding(node).generateAccess(
                                                isInsideInnerClass = true,
                                                builder = this,
                                                inside = thisGraph,
                                                kind = kind,
                                            )
                                        }
                                    }
                                }
                                +")"
                            }
                        }
                    }
                }
            }
        }
    }
}