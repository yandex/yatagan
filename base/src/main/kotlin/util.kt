package com.yandex.daggerlite.base

import java.util.ServiceLoader
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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