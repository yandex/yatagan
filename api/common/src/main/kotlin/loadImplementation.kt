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

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.Component

/**
 * Must be used on root components with declared builder.
 */
@Throws(ClassNotFoundException::class)
fun <T : Any> loadImplementationByBuilderClass(builderClass: Class<T>): T {
    require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
        "$builderClass is not a builder for a Yatagan component"
    }
    val componentClass = requireNotNull(builderClass.enclosingClass) {
        "No enclosing component class found for $builderClass"
    }
    val yataganComponentClass = loadImplementationClass(componentClass)
    return builderClass.cast(yataganComponentClass.getDeclaredMethod("builder").invoke(null))
}

/**
 * Must be used on root components with no builder.
 */
@Throws(ClassNotFoundException::class)
fun <T : Any> loadAutoBuilderImplementationByComponentClass(componentClass: Class<T>): AutoBuilder<T> {
    val yataganComponentClass = loadImplementationClass(componentClass)
    val autoBuilder = try {
        yataganComponentClass.getDeclaredMethod("autoBuilder")
    } catch (_: NoSuchMethodException) {
        throw IllegalArgumentException(
            "Auto-builder can't be used for $componentClass, because it declares an explicit builder. " +
                    "Please use `Yatagan.builder()` instead"
        )
    }
    @Suppress("UNCHECKED_CAST")
    return autoBuilder.invoke(null) as AutoBuilder<T>
}
