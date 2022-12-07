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

import com.yandex.yatagan.lang.TypeDeclaration

abstract class TypeDeclarationBase : TypeDeclaration {
    final override fun toString() = asType().toString()

    final override fun compareTo(other: TypeDeclaration): Int {
        if (this == other) return 0

        // Compare by qualified name - cheap
        qualifiedName.compareTo(other.qualifiedName).let { if (it != 0) return it }
        // Compare by implicit type-arguments - possibly more expensive
        return asType().compareTo(other.asType())
    }
}