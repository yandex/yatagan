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

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtFieldBase
import com.yandex.yatagan.lang.scope.LexicalScope
import javax.lang.model.element.VariableElement

internal class JavaxFieldImpl (
    override val owner: JavaxTypeDeclarationImpl,
    private val impl: VariableElement,
) : CtFieldBase(), CtAnnotated by JavaxAnnotatedImpl(owner, impl), LexicalScope by owner {
    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val type: Type by lazy {
        JavaxTypeImpl(asMemberOf(owner.type, impl))
    }

    override val name: String get() = impl.simpleName.toString()

    override val platformModel: VariableElement get() = impl
}