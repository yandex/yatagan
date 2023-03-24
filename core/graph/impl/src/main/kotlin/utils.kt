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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.Extensible
import com.yandex.yatagan.core.graph.WithParents
import com.yandex.yatagan.core.model.ComponentFactoryModel

internal fun <K, V> mergeMultiMapsForDuplicateCheck(
    fromParent: Map<K, List<V>>?,
    current: Map<K, List<V>>,
): Map<K, List<V>> {
    fromParent ?: return current
    return buildMap<K, MutableList<V>> {
        fromParent.forEach { (k, values) ->
            // Do not include inherited duplicates, they should be checked separately.
            put(k, arrayListOf(values.first()))
        }
        current.forEach { (k, values) ->
            val alreadyPresent = get(k)
            if (alreadyPresent != null) {
                alreadyPresent += values
            } else {
                put(k, values.toMutableList())
            }
        }
    }
}

/**
 * Allows implementing [WithParents] trait by delegating to [Extensible] trait.
 */
internal fun <C, P> hierarchyExtension(
    delegate: P,
    key: Extensible.Key<C>,
): WithParents<C> where C : WithParents<C>, P : WithParents<P>, P : Extensible {
    return object : WithParents<C> {
        override val parent: C?
            get() = delegate.parent?.let { it[key] }
    }
}

internal interface ComponentFactoryVisitor<R> : ComponentFactoryModel.Visitor<R> {
    fun visitNull(): R
}

internal fun <R> ComponentFactoryModel?.accept(visitor: ComponentFactoryVisitor<R>): R {
    return this?.accept(visitor) ?: visitor.visitNull()
}