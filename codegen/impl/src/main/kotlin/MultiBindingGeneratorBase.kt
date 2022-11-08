package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding

internal abstract class MultiBindingGeneratorBase<B : Binding>(
    private val bindings: List<B>,
    methodsNs: Namespace,
    accessorPrefix: String,
) : ComponentGenerator.Contributor {
    private val accessorNames: Map<B, String> = bindings.associateWith {
        methodsNs.name(
            nameModel = it.target.name,
            prefix = accessorPrefix,
        )
    }

    fun generateCreation(
        builder: ExpressionBuilder,
        binding: B,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) {
        with(builder) {
            +"%L.%N()".formatCode(componentForBinding(
                inside = inside,
                binding = binding,
                isInsideInnerClass = isInsideInnerClass,
            ), accessorNames[binding]!!)
        }
    }

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        for (binding in bindings) {
            method(accessorNames[binding]!!) {
                modifiers(/*package-private*/)
                returnType(binding.target.typeName())
                buildAccessorCode(
                    builder = this,
                    binding = binding,
                )
            }
        }
    }

    abstract fun buildAccessorCode(
        builder: CodeBuilder,
        binding: B,
    )
}