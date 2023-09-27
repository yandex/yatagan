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

import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member

abstract class FieldBase : Field {
    final override fun <R> accept(visitor: Member.Visitor<R>): R {
        return visitor.visitField(this)
    }

    final override fun toString() = "$owner::$name: $type"

    final override fun compareTo(other: Field): Int {
        if (this === other) return 0

        name.compareTo(other.name).let { if (it != 0) return it }
        owner.compareTo(other.owner).let { if (it != 0) return it }
        type.compareTo(other.type).let { if (it != 0) return it }

        return 0
    }
}