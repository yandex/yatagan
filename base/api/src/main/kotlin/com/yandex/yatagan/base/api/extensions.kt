/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.base.api

/**
 * Generate a sequence of parents, optionally including `this`.
 *
 * @param includeThis whether to start the sequence with the current node.
 * @see WithParents
 */
public fun <P : WithParents<P>> P.parentsSequence(
    includeThis: Boolean = false,
): Sequence<P> {
    return object : Sequence<P> {
        val initial = if (includeThis) this@parentsSequence else parent
        override fun iterator() = object : Iterator<P> {
            var next: P? = initial
            override fun hasNext() = next != null
            override fun next() = (next ?: throw NoSuchElementException()).also { next = it.parent }
        }
    }
}

/**
 * Generate a sequence, traversing all the children using BFS.
 *
 * @param includeThis whether to start the sequence with the current node or with just its first child.
 */
public fun <C : WithChildren<C>> C.childrenSequence(
    includeThis: Boolean = true,
): Sequence<C> {
    return object : Sequence<C> {
        val initial: Collection<C> = if (includeThis) listOf(this@childrenSequence) else children

        override fun iterator() = object : Iterator<C> {
            val queue = ArrayDeque(initial)

            override fun hasNext(): Boolean = queue.isNotEmpty()

            override fun next(): C {
                val next = queue.removeFirst()
                queue.addAll(next.children)
                return next
            }
        }
    }
}