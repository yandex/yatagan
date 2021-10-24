package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.inject.Provider
import javax.lang.model.element.Modifier.PRIVATE

internal class SlotSwitchingGenerator(
    private val methodsNs: Namespace,
    private val provisionGenerator: Provider<ProvisionGenerator>,
) : ComponentGenerator.Contributor {

    private val boundSlots = mutableMapOf<Binding, Int>()
    private var nextFreeSlot = 0

    fun requestSlot(forBinding: Binding): Int {
        return boundSlots.getOrPut(forBinding) { nextFreeSlot++ }
    }

    override fun generate(builder: TypeSpecBuilder) {
        builder.method(methodsNs.name("_new")) {
            modifiers(PRIVATE)
            returnType(ClassName.OBJECT)
            parameter(ClassName.INT, "slot")
            controlFlow("switch(slot)") {
                boundSlots.forEach { (binding, slot) ->
                    +buildExpression {
                        +"case $slot: return "
                        provisionGenerator.get().generateAccess(this, binding)
                    }
                }
                +"default: throw new %T()".formatCode(Names.AssertionError)
            }
        }
    }
}