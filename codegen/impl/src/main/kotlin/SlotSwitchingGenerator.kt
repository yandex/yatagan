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

import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

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
        builder.method(
            name = FactoryMethodName,
            access = Access.Internal,
        ) {
            returnType(TypeName.AnyObject)
            parameter(TypeName.Int, "slot")
            if (maxSlotsPerSwitch > 1 && bindings.size > maxSlotsPerSwitch) {
                // Strategy with two-level-nested switches

                val outerSlotsCount = bindings.size / maxSlotsPerSwitch

                for(outerSlot in 0 .. outerSlotsCount) {
                    builder.method(
                        name = "$FactoryMethodName\$$outerSlot",
                        access = Access.Private,
                    ) {
                        returnType(TypeName.AnyObject)
                        parameter(TypeName.Int, "slot")
                        code {
                            val startIndex = outerSlot * maxSlotsPerSwitch
                            val nestedSlotsCount = min(bindings.size - startIndex, maxSlotsPerSwitch)
                            appendSwitchingControlFlow(
                                subject = { append("slot") },
                                numberOfCases = nestedSlotsCount,
                                caseValue = { append(it.toString()) },
                                caseBlock = {
                                    val binding = bindings[startIndex + it]
                                    appendReturnStatement {
                                        binding.generateAccess(
                                            builder = this,
                                            inside = thisGraph,
                                            isInsideInnerClass = false,
                                        )
                                    }
                                },
                                defaultCaseBlock = {
                                    appendStatement { append("throw ").appendObjectCreation(TypeName.AssertionError) }
                                }
                            )
                        }
                    }
                }

                code {
                    appendSwitchingControlFlow(
                        subject = { append("slot / $maxSlotsPerSwitch") },
                        numberOfCases = outerSlotsCount + 1,
                        caseValue = { outerSlot ->
                            append(outerSlot.toString())
                        },
                        caseBlock = { outerSlot ->
                            appendReturnStatement {
                                appendName("$FactoryMethodName\$$outerSlot")
                                    .append("(slot % 100)")
                            }
                        },
                        defaultCaseBlock = {
                            appendStatement { append("throw ").appendObjectCreation(TypeName.AssertionError) }
                        },
                    )
                }
            } else {
                // Single switch statement

                code {
                    appendSwitchingControlFlow(
                        subject = { append("slot") },
                        numberOfCases = bindings.size,
                        caseValue = { append(it.toString()) },
                        caseBlock = {
                            val binding = bindings[it]
                            appendReturnStatement {
                                binding.generateAccess(
                                    builder = this,
                                    inside = thisGraph,
                                    isInsideInnerClass = false,
                                )
                            }
                        },
                        defaultCaseBlock = {
                            appendStatement { append("throw ").appendObjectCreation(TypeName.AssertionError) }
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val FactoryMethodName = "switch\$\$access"
    }
}