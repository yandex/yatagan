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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UnscopedProviderGenerator @Inject constructor(
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    private var isUsed = false
    val name: ClassName = componentImplName.nestedClass("ProviderImpl")
        get() = field.also { isUsed = true }

    override fun generate(builder: TypeSpecBuilder) {
        if (!isUsed) return
        val typeVar = TypeName.TypeVariable("T")
        builder.nestedClass(
            name = name,
            isInner = false,
            access = Access.Internal,
        ) {
            generic(typeVar)
            implements(TypeName.Lazy(typeVar))
            field(
                type = componentImplName,
                name = "mDelegate",
                isMutable = false,
                access = Access.Private,
            ) {}
            field(
                type = TypeName.Int,
                name = "mIndex",
                isMutable = false,
                access = Access.Private,
            ) {}
            primaryConstructor(
                access = Access.Internal,
            ) {
                parameter(componentImplName, "delegate")
                parameter(TypeName.Int, "index")
                code {
                    appendStatement { append("this.mDelegate = delegate") }
                    appendStatement { append("this.mIndex = index") }
                }
            }
            method(
                name = "get",
                access = Access.Public,
            ) {
                manualOverride()
                returnType(typeVar)
                code {
                    appendReturnStatement {
                        appendCast(
                            asType = typeVar,
                            expression = {
                                append("this.mDelegate.").appendName(SlotSwitchingGenerator.FactoryMethodName)
                                    .append("(this.mIndex)")
                            }
                        )
                    }
                }
            }
        }
    }
}