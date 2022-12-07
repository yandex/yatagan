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

private class MemoizedSequence<T>(sequence: Sequence<T>) : Sequence<T> {
    @Volatile
    private var sequence: Sequence<T>? = sequence

    private var iterator: Iterator<T>? = null
    private val cache = ArrayList<T>()

    fun getIterator(): Iterator<T> {
        var local = iterator
        if (local == null) {
            local = sequence!!.iterator()
            iterator = local
        }
        return local
    }

    private inner class CachingIterator : Iterator<T> {
        var index = 0

        override fun hasNext(): Boolean {
            synchronized(cache) {
                if (index < cache.size) {
                    return true
                }
                if (sequence == null) {
                    // Sequence is already done, no more elements
                    return false
                }
                if (!getIterator().hasNext()) {
                    // Sequence is done
                    sequence = null
                    iterator = null
                    return false
                }
                return true
            }
        }

        override fun next(): T {
            return synchronized(cache) {
                (if (index == cache.size) {
                    getIterator().next().also { cache += it }
                } else cache[index]).also { index++ }
            }
        }
    }

    override fun iterator(): Iterator<T> {
        val local = sequence
        return if (local == null) {
            cache.iterator()
        } else {
            synchronized(cache) {
                if (sequence != null) CachingIterator() else cache.iterator()
            }
        }
    }
}

fun <T> Sequence<T>.memoize(): Sequence<T> = when (this) {
    is MemoizedSequence -> this
    else -> MemoizedSequence(this)
}