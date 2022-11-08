package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding

internal class SlotSwitchingGenerator(
    private val thisGraph: BindingGraph,
) : ComponentGenerator.Contributor {

    private val boundSlots = mutableMapOf<Binding, Int>()
    private var nextFreeSlot = 0

    fun requestSlot(forBinding: Binding): Int {
        return boundSlots.getOrPut(forBinding) { nextFreeSlot++ }
    }

    override fun generate(builder: TypeSpecBuilder) {
        builder.method(FactoryMethodName) {
            modifiers(/*package-private*/)
            returnType(ClassName.OBJECT)
            parameter(ClassName.INT, "slot")
            controlFlow("switch(slot)") {
                boundSlots.forEach { (binding, slot) ->
                    +buildExpression {
                        +"case $slot: return "
                        binding.generateAccess(
                            builder = this,
                            inside = thisGraph,
                            isInsideInnerClass = false,
                        )
                    }
                }
                +"default: throw new %T()".formatCode(Names.AssertionError)
            }
        }
    }

    companion object {
        const val FactoryMethodName = "switch\$\$access"
    }
}