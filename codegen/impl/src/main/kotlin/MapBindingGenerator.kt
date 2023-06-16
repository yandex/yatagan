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

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Type

internal class MapBindingGenerator(
    methodsNs: Namespace,
    private val thisGraph: BindingGraph,
) : MultiBindingGeneratorBase<MapBinding>(
    methodsNs = methodsNs,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MapBinding>(),
    accessorPrefix = "mapOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MapBinding) = with(builder) {
        +"final %T map = new %T<>(${binding.contents.size})"
            .formatCode(binding.target.typeName(), Names.HashMap)
        binding.upstream?.let { upstream ->
            +buildExpression {
                +"map.putAll("
                upstream.generateAccess(
                    builder = this,
                    inside = thisGraph,
                    isInsideInnerClass = false,
                )
                +")"
            }
        }
        binding.contents.forEach { contribution ->
            val (node, kind) = contribution.dependency
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                binding = nodeBinding,
                inside = thisGraph,
                isInsideInnerClass = false,
            ) {
                +buildExpression {
                    +"map.put("
                    contribution.keyValue.accept(AnnotationValueFormatter(this))
                    +", "
                    nodeBinding.generateAccess(
                        builder = this,
                        inside = thisGraph,
                        kind = kind,
                        isInsideInnerClass = false,
                    )
                    +")"
                }
            }
        }
        +"return map"
    }

    private class AnnotationValueFormatter(
        val builder: ExpressionBuilder,
    ) : Annotation.Value.Visitor<Unit> {
        override fun visitDefault(value: Any?) = throw AssertionError()
        override fun visitBoolean(value: Boolean) = with(builder) { +value.toString() }
        override fun visitByte(value: Byte) = with(builder) { +value.toString() }
        override fun visitShort(value: Short) = with(builder) { +value.toString() }
        override fun visitInt(value: Int) = with(builder) { +value.toString() }
        override fun visitLong(value: Long) = with(builder) { +value.toString() }
        override fun visitChar(value: Char) = with(builder) { +"'%L'".formatCode(value) }
        override fun visitFloat(value: Float) = with(builder) { +value.toString() }
        override fun visitDouble(value: Double) = with(builder) { +value.toString() }
        override fun visitString(value: String) = with(builder) { +"%S".formatCode(value) }
        override fun visitType(value: Type) = with(builder) { +"%T.class".formatCode(value.typeName()) }
        override fun visitAnnotation(value: Annotation) = with(builder) { +"<unsupported>" }
        override fun visitArray(value: List<Annotation.Value>) = with(builder) { +"<unsupported>" }
        override fun visitUnresolved() = with(builder) { +"<unresolved>" }
        override fun visitEnumConstant(enum: Type, constant: String) = with(builder) {
            +"%T.%N".formatCode(enum.typeName(), constant)
        }
    }

    companion object Key : Extensible.Key<MapBindingGenerator> {
        override val keyType get() = MapBindingGenerator::class.java
    }
}