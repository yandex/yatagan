package com.yandex.daggerlite.base

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