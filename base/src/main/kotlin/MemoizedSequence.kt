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