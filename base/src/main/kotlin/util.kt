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

package com.yandex.yatagan.base

import java.util.ServiceLoader
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

inline fun <R> ifOrElseNull(condition: Boolean, block: () -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (condition) block() else null
}

inline fun <reified S : Any> loadServices(): List<S> {
    val serviceClass = S::class.java
    return ServiceLoader.load(serviceClass, serviceClass.classLoader).toList()
}

inline fun <T, reified R> Array<T>.mapToArray(map: (T) -> R): Array<R> {
    contract { callsInPlace(map) }
    return Array(size) { map(get(it)) }
}

inline fun <T, reified R> List<T>.mapToArray(map: (T) -> R): Array<R> {
    contract { callsInPlace(map) }
    return Array(size) { map(get(it)) }
}

fun <T1, T2> Sequence<T1>.zipOrNull(another: Sequence<T2>): Sequence<Pair<T1?, T2?>> {
    return Sequence {
        object : Iterator<Pair<T1?, T2?>> {
            val iterator1 = this@zipOrNull.iterator()
            val iterator2 = another.iterator()

            override fun hasNext() = iterator1.hasNext() || iterator2.hasNext()
            override fun next(): Pair<T1?, T2?> {
                val v1 = ifOrElseNull(iterator1.hasNext()) { iterator1.next() }
                val v2 = ifOrElseNull(iterator2.hasNext()) { iterator2.next() }
                return v1 to v2
            }
        }
    }
}

inline fun <T, R> Iterable<T>.zipWithNextOrNull(block: (T, T?) -> R): List<R> {
    contract { callsInPlace(block) }

    val iterator = iterator()
    if (!iterator.hasNext()) {
        return emptyList()
    }

    val iteratorNext = iterator().also { it.next() }

    val list: MutableList<R> = when (this) {
        is Collection<*> -> ArrayList(size - 1)
        else -> ArrayList()
    }
    while (iterator.hasNext()) {
        val first = iterator.next()
        val second = ifOrElseNull(iterator.hasNext()) { iteratorNext.next() }
        list.add(block(first, second))
    }
    return list
}

inline fun <T : Any> traverseDepthFirstWithPath(
    roots: Iterable<T>,
    childrenOf: (T) -> Iterable<T>,
    onLoop: ((loop: Sequence<T>) -> Unit) = { /* nothing by default */ },
    visit: (path: Sequence<T>, node: T) -> Unit = { _, _ -> /* nothing by default */ },
) {
    val markedGray = hashSetOf<T>()
    val markedBlack = hashSetOf<T>()
    val stack = arrayListOf<MutableList<T>>()

    val currentPath = stack.asSequence().map { it.last() }

    stack += roots.toMutableList()

    while (stack.isNotEmpty()) {
        // Substack is introduced to preserve node hierarchy
        val subStack = stack.last()
        if (subStack.isEmpty()) {
            stack.removeLast()
            continue
        }

        when (val node = subStack.last()) {
            in markedBlack -> {
                subStack.removeLast()
            }

            in markedGray -> {
                subStack.removeLast()
                markedBlack += node
                markedGray -= node
            }

            else -> {
                markedGray += node
                visit(currentPath, node)
                stack += childrenOf(node).mapNotNullTo(arrayListOf()) { child ->
                    if (child in markedGray) {
                        // Loop: report and skip
                        onLoop(currentPath.dropWhile { it != child })
                        null
                    } else child
                }
            }
        }
    }
}

class SingleInitPropertyWithFallbackDelegate<T : Any>(
    private val fallbackValue: () -> T,
) {
    @Volatile private var valueHolder: Lazy<T> = lazy(fallbackValue)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = valueHolder.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        check(!valueHolder.isInitialized()) {
            "$property was already initialized, can't initialize after usage or twice"
        }
        valueHolder = object : Lazy<T> {
            override val value: T get() = value
            override fun isInitialized() = true
        }
    }

    fun deinitialize() {
        valueHolder = lazy(fallbackValue)
    }

    fun isInitialized(): Boolean {
        return valueHolder.isInitialized()
    }
}

fun <T : Any> singleInitWithFallback(fallback: () -> T) = SingleInitPropertyWithFallbackDelegate(fallback)