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

class ListComparator<T>(
    private val comparator: Comparator<T>,
    private val asSorted: Boolean,
) : Comparator<List<T>> {

    override fun compare(o1: List<T>, o2: List<T>): Int {
        if (o1.size != o2.size) return o1.size.compareTo(o2.size)

        val sorted1 = if (asSorted) o1.sortedWith(comparator) else o1
        val sorted2 = if (asSorted) o2.sortedWith(comparator) else o2
        for (i in sorted1.indices) {
            val v = comparator.compare(sorted1[i], sorted2[i])
            if (v != 0) return v
        }

        return 0
    }

    companion object {
        fun <T : Comparable<T>> ofComparable(
            asSorted: Boolean,
        ) = ListComparator<T>(
            comparator = Comparabletor.obtain(),
            asSorted = asSorted,
        )
    }
}

class MapComparator<K, V>(
    private val keyComparator: Comparator<K>,
    private val valueComparator: Comparator<V>,
) : Comparator<Map<K, V>> {
    override fun compare(o1: Map<K, V>, o2: Map<K, V>): Int {
        if (o1.size != o2.size) return o1.size.compareTo(o2.size)
        val keys1 = o1.keys.sortedWith(keyComparator)
        val keys2 = o2.keys.sortedWith(keyComparator)
        ListComparator(keyComparator, asSorted = false /* already sorted*/)
            .compare(keys1, keys2).let { if (it != 0) return it }
        for (k in keys1) {
            valueComparator.compare(o1[k]!!, o2[k]!!).let { if (it != 0) return it }
        }
        return 0
    }

    companion object {
        fun <K : Comparable<K>, V> ofComparableKey(
            valueComparator: Comparator<V>,
        ) = MapComparator<K, V>(
            keyComparator = Comparabletor.obtain(),
            valueComparator = valueComparator,
        )

        fun <K, V : Comparable<V>> ofComparableValue(
            keyComparator: Comparator<K>,
        ) = MapComparator<K, V>(
            keyComparator = keyComparator,
            valueComparator = Comparabletor.obtain(),
        )

        fun <K : Comparable<K>, V : Comparable<V>> ofComparable(
        ) = MapComparator<K, V>(
            keyComparator = Comparabletor.obtain(),
            valueComparator = Comparabletor.obtain(),
        )
    }
}

// Yes, a comparator for comparable - comparabletor
private object Comparabletor : Comparator<Comparable<Any>> {
    override fun compare(o1: Comparable<Any>, o2: Comparable<Any>): Int = o1.compareTo(o2)

    fun <T : Comparable<T>> obtain(): Comparator<T> {
        @Suppress("UNCHECKED_CAST")
        return this as Comparator<T>
    }
}
