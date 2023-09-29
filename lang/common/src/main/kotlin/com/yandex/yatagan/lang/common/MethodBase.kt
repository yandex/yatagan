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

import com.yandex.yatagan.base.zipOrNull
import com.yandex.yatagan.lang.Callable
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method

abstract class MethodBase : Method {
    final override fun <T> accept(visitor: Callable.Visitor<T>): T {
        return visitor.visitMethod(this)
    }

    final override fun <R> accept(visitor: Member.Visitor<R>): R {
        return visitor.visitMethod(this)
    }

    final override fun compareTo(other: Method): Int {
        if (this == other) return 0

        name.compareTo(other.name).let { if (it != 0) return it }
        owner.compareTo(other.owner).let { if (it != 0) return it }
        for ((p1, p2) in parameters.zipOrNull(other.parameters)) {
            if (p1 == null) return -1
            if (p2 == null) return +1
            p1.type.compareTo(p2.type).let { if (it != 0) return it }
        }
        returnType.compareTo(other.returnType).let { if (it != 0) return it }
        return 0
    }

    final override fun toString() = buildString {
        append(owner)
        append("::")
        append(name).append('(')
        parameters.joinTo(this)
        append("): ")
        append(returnType)
    }
}