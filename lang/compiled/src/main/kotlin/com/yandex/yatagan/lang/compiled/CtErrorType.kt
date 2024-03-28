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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.common.ErrorType
import com.yandex.yatagan.lang.scope.LexicalScope

class CtErrorType(
    lexicalScope: LexicalScope,
    override val nameModel: InvalidNameModel,
    isUnresolved: Boolean,
) : ErrorType(
    lexicalScope = lexicalScope,
    nameHint = nameModel.toString(),
    isUnresolved = isUnresolved,
), CtNamedType

fun LexicalScope.CtErrorType(
    nameModel: InvalidNameModel,
    isUnresolved: Boolean = true,
) : CtErrorType {
    return CtErrorType(
        lexicalScope = this,
        nameModel = nameModel,
        isUnresolved = isUnresolved,
    )
}