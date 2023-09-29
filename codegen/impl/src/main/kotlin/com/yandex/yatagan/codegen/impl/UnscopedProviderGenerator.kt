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
import com.yandex.yatagan.codegen.poetry.buildClass
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

@Singleton
internal class UnscopedProviderGenerator @Inject constructor(
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    private var isUsed = false
    val name: ClassName = componentImplName.nestedClass("ProviderImpl")
        get() = field.also { isUsed = true }

    override fun generate(builder: TypeSpecBuilder) {
        if (!isUsed) return
        builder.nestedType {
            buildClass(name) {
                implements(Names.Lazy)
                modifiers(/*package-private*/ STATIC, FINAL)
                field(componentImplName, "mDelegate") {
                    modifiers(PRIVATE, FINAL)
                }
                field(ClassName.INT, "mIndex") {
                    modifiers(PRIVATE, FINAL)
                }
                constructor {
                    parameter(componentImplName, "delegate")
                    parameter(ClassName.INT, "index")
                    +"this.mDelegate = delegate"
                    +"this.mIndex = index"
                }
                method("get") {
                    modifiers(PUBLIC)
                    annotation<Override>()
                    returnType(ClassName.OBJECT)
                    +"return this.mDelegate.%N(this.mIndex)".formatCode(SlotSwitchingGenerator.FactoryMethodName)
                }
            }
        }
    }
}