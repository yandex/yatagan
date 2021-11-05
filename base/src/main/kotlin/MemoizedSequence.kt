package com.yandex.daggerlite.base

private class MemoizedSequence<T>(sequence: Sequence<T>) : Sequence<T> {
    private val cache = mutableListOf<T>()
    private val iterator: Iterator<T> by lazy(LazyThreadSafetyMode.NONE) {
        sequence.iterator()
    }

    private inner class CachedIterator(val iteratorCache: Iterator<T>, var buffer: Int) : Iterator<T> {
        override fun hasNext() = buffer > 0 || iterator.hasNext()
        override fun next(): T {
            if (buffer > 0) {
                buffer--
                return iteratorCache.next()
            }
            val value = iterator.next()
            cache.add(value)
            return value
        }
    }

    override fun iterator(): Iterator<T> = CachedIterator(cache.iterator(), cache.size)
}

fun <T> Sequence<T>.memoize(): Sequence<T> = when (this) {
    is MemoizedSequence -> this
    else -> MemoizedSequence(this)
}