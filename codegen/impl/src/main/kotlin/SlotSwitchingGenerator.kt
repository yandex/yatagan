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