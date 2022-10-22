package com.yandex.daggerlite.testing.generation

internal class Tree<T> private constructor(
    val value: T,
    val parent: Tree<T>?,
    val depth: Int,
) {
    private val _children = mutableListOf<Tree<T>>()
    val children: List<Tree<T>> get() = _children

    fun addChild(value: T): Tree<T> {
        return Tree(
            value = value,
            parent = this,
            depth = depth + 1,
        ).also(_children::add)
    }

    companion object {
        fun <T> createTree(rootValue: T): Tree<T> {
            return Tree(rootValue, parent = null, depth = 1)
        }
    }
}

internal fun <T> Tree<T>.walk(visitor: (Tree<T>) -> Unit) {
    visitor(this)
    val walkQueue = ArrayDeque(children)
    while(walkQueue.isNotEmpty()) {
        val node = walkQueue.removeFirst()
        visitor(node)
        walkQueue.addAll(node.children)
    }
}

internal fun <T> Tree<T>.pathToRoot(): Sequence<Tree<T>> {
    return Sequence { object : Iterator<Tree<T>> {
        var next: Tree<T>? = this@pathToRoot
        override fun hasNext() = next != null
        override fun next() = next?.also { next = it.parent } ?: throw NoSuchElementException()
    } }
}

internal class Forest<T> {
    private val _trees = mutableListOf<Tree<T>>()

    val trees: List<Tree<T>> get() = _trees

    fun addTree(rootValue: T): Tree<T> {
        return Tree.createTree(rootValue).also(_trees::add)
    }
}

internal fun <T> Forest<T>.walk(visitor: (Tree<T>) -> Unit) {
    for (tree in trees) {
        tree.walk(visitor)
    }
}