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

@file:[JvmMultifileClass JvmName("Loader")]
package com.yandex.yatagan.common

import com.yandex.yatagan.Component

internal fun loadImplementationClass(componentClass: Class<*>): Class<*> {
    require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
        "$componentClass is not a root Yatagan component"
    }

    // Keep name mangling in sync with codegen!
    val (packageName, binaryName) = splitComponentName(componentClass)
    // no need to parse and join simple names, as codegen joins them with '$' and
    // that's what JVM binary class name already is.
    val implementationName = "$packageName.Yatagan\$$binaryName"

    return componentClass.classLoader.loadClass(implementationName)
}

private fun splitComponentName(clazz: Class<*>): Pair<String, String> {
    val name = clazz.name
    return when(val lastDot = name.lastIndexOf('.')) {
        -1 -> "" to name
        else -> name.substring(0, lastDot) to name.substring(lastDot + 1)
    }
}
