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

package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.scope.LexicalScope

open class ErrorType(
    lexicalScope: LexicalScope,
    private val nameHint: String,
    final override val isUnresolved: Boolean = true,
) : TypeBase(), LexicalScope by lexicalScope {
    final override val declaration: NoDeclaration
        get() = NoDeclaration(this)

    final override val typeArguments: List<Nothing>
        get() = emptyList()

    final override val isVoid: Boolean
        get() = false

    final override fun isAssignableFrom(another: Type): Boolean {
        return this === another
    }

    final override fun asBoxed(): Type {
        return this
    }

    final override fun toString() = nameHint
}

fun LexicalScope.ErrorType(
    nameHint: String,
    isUnresolved: Boolean = true,
): ErrorType = ErrorType(
    lexicalScope = this,
    nameHint = nameHint,
    isUnresolved = isUnresolved,
)