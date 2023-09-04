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

@file:Suppress("NOTHING_TO_INLINE")

package com.yandex.yatagan.base

import java.util.EnumMap
import java.util.EnumSet

private class PairSet<out T>(
    private val one: T,
    private val two: T,
) : AbstractSet<T>() {
    override val size get() = 2
    override fun isEmpty() = false
    override fun contains(element: @UnsafeVariance T) = one == element || two == element
    override fun containsAll(elements: Collection<@UnsafeVariance T>) = elements.all { it == one || it == two }
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var next = 1
        override fun hasNext() = next < 3
        override fun next(): T = when (next++) {
            1 -> one; 2 -> two; else -> throw NoSuchElementException()
        }
    }
}

fun <T> setOf(one: T, two: T): Set<T> {
    return if (one == two) setOf(one) else PairSet(one, two)
}

infix fun <T> Set<T>.intersects(another: Set<T>): Boolean {
    if (isEmpty() || another.isEmpty()) {
        return false
    }
    if (size < another.size) {
        for (element in this) if (element in another)
            return true
    } else {
        for (element in another) if (element in this)
            return true
    }
    return false
}

inline infix fun <T> Set<T>.notIntersects(another: Set<T>) = !(this intersects another)

// region enumSetOf

inline fun <reified E : Enum<E>> enumSetOf(): EnumSet<E> = EnumSet.noneOf(E::class.java)

fun <E : Enum<E>> enumSetOf(value: E): EnumSet<E> = EnumSet.of(value)

fun <E : Enum<E>> enumSetOf(v1: E, v2: E): EnumSet<E> = EnumSet.of(v1, v2)

fun <E : Enum<E>> enumSetOf(v1: E, v2: E, v3: E): EnumSet<E> = EnumSet.of(v1, v2, v3)

fun <E : Enum<E>> enumSetOf(v1: E, v2: E, v3: E, v4: E): EnumSet<E> = EnumSet.of(v1, v2, v3, v4)

fun <E : Enum<E>> enumSetOf(v1: E, vararg rest: E): EnumSet<E> = EnumSet.of(v1, *rest)

// endregion

// region enumMapOf

inline fun <reified K : Enum<K>, V> enumMapOf(): EnumMap<K, V> = EnumMap<K, V>(K::class.java)

inline fun <reified K : Enum<K>, V> enumMapOf(key: K, value: V): EnumMap<K, V> =
    enumMapOf<K, V>().apply { put(key, value) }

inline fun <reified K : Enum<K>, V> enumMapOf(kv1: Pair<K, V>, kv2: Pair<K, V>): EnumMap<K, V> =
    enumMapOf<K, V>().apply {
        put(kv1.first, kv1.second)
        put(kv2.first, kv2.second)
    }

inline fun <reified K : Enum<K>, V> enumMapOf(kv1: Pair<K, V>, kv2: Pair<K, V>, kv3: Pair<K, V>): EnumMap<K, V> =
    enumMapOf<K, V>().apply {
        put(kv1.first, kv1.second)
        put(kv2.first, kv2.second)
        put(kv3.first, kv3.second)
    }

inline fun <reified K : Enum<K>, V> enumMapOf(kv1: Pair<K, V>, kv2: Pair<K, V>, kv3: Pair<K, V>, kv4: Pair<K, V>): EnumMap<K, V> =
    enumMapOf<K, V>().apply {
        put(kv1.first, kv1.second)
        put(kv2.first, kv2.second)
        put(kv3.first, kv3.second)
        put(kv4.first, kv4.second)
    }

inline fun <reified K : Enum<K>, V> enumMapOf(vararg pairs: Pair<K, V>): EnumMap<K, V> =
    enumMapOf<K, V>().apply { pairs.forEachBi(::put) }

// endregion
