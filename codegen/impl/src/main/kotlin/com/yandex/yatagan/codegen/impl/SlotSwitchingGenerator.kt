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
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier

@Singleton
internal class SlotSwitchingGenerator @Inject constructor(
    private val thisGraph: BindingGraph,
    options: ComponentGenerator.Options,
) : ComponentGenerator.Contributor {
    private val maxSlotsPerSwitch = options.maxSlotsPerSwitch

    init {
        require(maxSlotsPerSwitch > 1 || maxSlotsPerSwitch == -1) {
            "maxSlotsPerSwitch should be at least 2 or -1 (infinity)"
        }
    }

    private var isUsed = false
    private val bindings = arrayListOf<Binding>()
    private val seen = hashSetOf<Binding>()

    fun requestSlot(forBinding: Binding): Int {
        isUsed = true
        if (forBinding in seen) {
            return bindings.indexOf(forBinding)
        }
        bindings += forBinding
        seen += forBinding
        return bindings.lastIndex
    }

    override fun generate(builder: TypeSpecBuilder) {
        if (!isUsed) return
        builder.method(FactoryMethodName) {
            modifiers(/*package-private*/)
            returnType(ClassName.OBJECT)
            parameter(ClassName.INT, "slot")
            if (maxSlotsPerSwitch > 1 && bindings.size > maxSlotsPerSwitch) {
                // Strategy with two-level-nested switches

                val outerSlotsCount = bindings.size / maxSlotsPerSwitch
                controlFlow("switch(slot / $maxSlotsPerSwitch)") {
                    for(outerSlot in 0 .. outerSlotsCount) {
                        val nestedFactoryName = "$FactoryMethodName\$$outerSlot"
                        builder.method(nestedFactoryName) {
                            modifiers(Modifier.PRIVATE)
                            returnType(ClassName.OBJECT)
                            parameter(ClassName.INT, "slot")
                            controlFlow("switch(slot)") {
                                val startIndex = outerSlot * maxSlotsPerSwitch
                                var nestedSlot = 0
                                while((startIndex + nestedSlot) < bindings.size && nestedSlot < maxSlotsPerSwitch) {
                                    val binding = bindings[startIndex + nestedSlot]
                                    +buildExpression {
                                        +"case $nestedSlot: return "
                                        binding.generateAccess(
                                            builder = this,
                                            inside = thisGraph,
                                            isInsideInnerClass = false,
                                        )
                                    }
                                    ++nestedSlot
                                }
                                +"default: throw new %T()".formatCode(Names.AssertionError)
                            }
                        }
                        +buildExpression {
                            +"case $outerSlot: return %N(slot %L 100)".formatCode(nestedFactoryName, "%")
                        }
                    }
                    +"default: throw new %T()".formatCode(Names.AssertionError)
                }
            } else {
                // Single switch statement

                controlFlow("switch(slot)") {
                    bindings.forEachIndexed { slot, binding ->
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
    }

    companion object {
        const val FactoryMethodName = "switch\$\$access"
    }
}