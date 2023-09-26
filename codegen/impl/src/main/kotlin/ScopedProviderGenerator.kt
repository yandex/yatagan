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
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.nestedClass
import com.yandex.yatagan.core.graph.BindingGraph
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ScopedProviderGenerator @Inject constructor(
    private val componentImplName: ClassName,
    private val options: ComponentGenerator.Options,
    graph: BindingGraph,
) : ComponentGenerator.Contributor {
    private var isUsed = false
    private val useDoubleChecking = graph.requiresSynchronizedAccess
    val name: ClassName = componentImplName.nestedClass(if (useDoubleChecking) "DoubleCheck" else "CachingProviderImpl")
        get() = field.also { isUsed = true }

    override fun generate(builder: TypeSpecBuilder) {
        if (!isUsed) return
        val typeVar = TypeName.TypeVariable("T")
        builder.nestedClass(
            name = name,
            access = Access.Private,
            isInner = false,
        ) {
            generic(typeVar)
            implements(TypeName.Lazy(typeVar))
            field(
                type = componentImplName,
                name = "mDelegate",
                access = Access.Private,
                isMutable = false,
            ) {}
            field(
                type = TypeName.Int,
                name = "mIndex",
                access = Access.Private,
                isMutable = false,
            ) {}
            field(
                type = TypeName.Nullable(TypeName.AnyObject),
                name = "mValue",
                access = Access.Private,
                isMutable = true,
            ) {
                if (useDoubleChecking) {
                    volatile()
                }
            }

            primaryConstructor(
                access = Access.Internal,
            ) {
                parameter(componentImplName, "factory")
                parameter(TypeName.Int, "index")
                code {
                    appendStatement { append("mDelegate = factory") }
                    appendStatement { append("mIndex = index") }
                }
            }

            method(
                name = "get",
                access = Access.Public,
            ) {
                manualOverride()
                returnType(typeVar)
                code {
                    appendVariableDeclaration(
                        type = TypeName.Nullable(TypeName.AnyObject),
                        name = "local",
                        mutable = true,
                        initializer = { append("mValue") }
                    )
                    appendIfControlFlow(
                        condition = { append("local == null") },
                        ifTrue = {
                            if (useDoubleChecking) {
                                appendSynchronizedBlock(
                                    lock = { append("this") },
                                ) {
                                    appendStatement { append("local = mValue") }
                                    appendIfControlFlow(
                                        condition = { append("local == null") },
                                        ifTrue = {
                                            appendStatement {
                                                append("local = mDelegate.")
                                                    .appendName(SlotSwitchingGenerator.FactoryMethodName)
                                                    .append("(mIndex)")
                                            }
                                            appendStatement { append("mValue = local") }
                                        },
                                    )
                                }
                            } else {
                                if (options.enableThreadChecks) {
                                    appendStatement {
                                        appendType(TypeName.ThreadAssertions).append(".assertThreadAccess()")
                                    }
                                }
                                appendStatement {
                                    append("local = mDelegate.")
                                        .appendName(SlotSwitchingGenerator.FactoryMethodName)
                                        .append("(mIndex)")
                                }
                                appendStatement { append("mValue = local") }
                            }
                        }
                    )
                    appendReturnStatement {
                        appendCast(
                            asType = typeVar,
                            expression = { append("local") },
                        )
                    }
                }
            }
        }
    }
}