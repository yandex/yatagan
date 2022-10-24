package com.yandex.daggerlite.codegen.impl

import com.yandex.daggerlite.codegen.poetry.CodeBuilder
import com.yandex.daggerlite.codegen.poetry.ExpressionBuilder
import com.yandex.daggerlite.codegen.poetry.buildExpression
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.Extensible
import com.yandex.daggerlite.core.graph.bindings.MapBinding
import com.yandex.daggerlite.core.model.component1
import com.yandex.daggerlite.core.model.component2
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.Type

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