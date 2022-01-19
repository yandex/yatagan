package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import javax.lang.model.element.Modifier.PRIVATE

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
            modifiers(PRIVATE)
            returnType(ClassName.OBJECT)
            parameter(ClassName.INT, "slot")
            controlFlow("switch(slot)") {
                boundSlots.forEach { (binding, slot) ->
                    +buildExpression {
                        +"case $slot: return "
                        binding.generateAccess(builder = this, inside = thisGraph)
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