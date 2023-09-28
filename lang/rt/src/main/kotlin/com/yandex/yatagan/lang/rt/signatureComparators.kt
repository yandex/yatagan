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

package com.yandex.yatagan.lang.rt

internal object MethodSignatureComparator : Comparator<ReflectMethod> {
    override fun compare(one: ReflectMethod, other: ReflectMethod): Int {
        one.name.compareTo(other.name).let { if (it != 0) return it }

        return parametersCompare(one.parameterTypes, other.parameterTypes)
    }
}

internal object ConstructorSignatureComparator : Comparator<ReflectConstructor> {
    override fun compare(one: ReflectConstructor, other: ReflectConstructor): Int {
        return parametersCompare(one.parameterTypes, other.parameterTypes)
    }
}

private fun parametersCompare(myParameterTypes: Array<Class<*>>, otherParameterTypes: Array<Class<*>>): Int {
    myParameterTypes.size.compareTo(otherParameterTypes.size).let { if (it != 0) return it }
    for (i in myParameterTypes.indices) {
        myParameterTypes[i].canonicalName
            .compareTo(otherParameterTypes[i].canonicalName).let { if (it != 0) return it }
    }
    return 0;
}