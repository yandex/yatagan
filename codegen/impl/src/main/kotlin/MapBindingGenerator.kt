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

import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MapBindingGenerator @Inject constructor(
    @MethodsNamespace methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : MultiBindingGeneratorBase<MapBinding>(
    methodsNs = methodsNs,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MapBinding>(),
    accessorPrefix = "mapOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MapBinding) {
        val type = binding.target.type
        val mapType = TypeName.HashMap(
            keyType = TypeName.Inferred(type.typeArguments[0]),
            valueType = TypeName.Inferred(type.typeArguments[1]),
        )
        builder.appendVariableDeclaration(
            type = mapType,
            name = "map",
            mutable = false,
            initializer = {
                appendObjectCreation(
                    type = mapType,
                    argumentCount = 1,
                    argument = { append(binding.contents.size.toString()) },
                )
            },
        )
        binding.upstream?.let { upstream ->
            builder.appendStatement {
                append("map.putAll(")
                upstream.generateAccess(
                    builder = this,
                    inside = thisGraph,
                    isInsideInnerClass = false,
                )
                append(")")
            }
        }
        binding.contents.forEach { contribution ->
            val (node, kind) = contribution.dependency
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                builder = builder,
                binding = nodeBinding,
                inside = thisGraph,
                isInsideInnerClass = false,
            ) {
                appendStatement {
                    append("map.put(")
                    contribution.keyValue.accept(AnnotationValueFormatter(this))
                    append(", ")
                    nodeBinding.generateAccess(
                        builder = this,
                        inside = thisGraph,
                        kind = kind,
                        isInsideInnerClass = false,
                    )
                    append(")")
                }
            }
        }
        builder.appendReturnStatement { append("map") }
    }

    private class AnnotationValueFormatter(
        val builder: ExpressionBuilder,
    ) : Annotation.Value.Visitor<ExpressionBuilder> {
        override fun visitDefault(value: Any?) = throw AssertionError()
        override fun visitBoolean(value: Boolean) = builder.append(value.toString())
        override fun visitByte(value: Byte) = builder.append(value.toString())
        override fun visitShort(value: Short) = builder.append(value.toString())
        override fun visitInt(value: Int) = builder.append(value.toString())
        override fun visitLong(value: Long) = builder.append(value.toString())
        override fun visitChar(value: Char) = builder.append("'").append(value.toString()).append("'")
        override fun visitFloat(value: Float) = builder.append(value.toString())
        override fun visitDouble(value: Double) = builder.append(value.toString())
        override fun visitString(value: String) = builder.appendString(value)
        override fun visitType(value: Type) = builder.appendClassLiteral(TypeName.Inferred(value))
        override fun visitAnnotation(value: Annotation) = builder.append("<unsupported>")
        override fun visitArray(value: List<Annotation.Value>) = builder.append("<unsupported>")
        override fun visitUnresolved() = builder.append("<unresolved>")
        override fun visitEnumConstant(enum: Type, constant: String) =
            builder.appendType(TypeName.Inferred(enum)).append(".").append(constant)
    }
}