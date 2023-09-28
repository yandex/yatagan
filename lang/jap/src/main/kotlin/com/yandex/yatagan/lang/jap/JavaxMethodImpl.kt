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

package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtMethodBase
import javax.lang.model.element.ExecutableElement

internal class JavaxMethodImpl (
    override val owner: JavaxTypeDeclarationImpl,
    private val impl: ExecutableElement,
) : CtMethodBase(), CtAnnotated by JavaxAnnotatedImpl(impl) {

    override val isAbstract: Boolean get() = impl.isAbstract

    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val returnType: Type by lazy {
        JavaxTypeImpl(impl.asMemberOf(owner.type).asExecutableType().returnType)
    }
    override val name: String get() = impl.simpleName.toString()

    override val parameters: Sequence<Parameter> = parametersSequenceFor(impl, owner.type)

    override val platformModel: ExecutableElement get() = impl
}