package com.yandex.daggerlite.core.graph

/**
 * A hierarchy trait, that allows accessing child nodes.
 */
interface WithChildren<C> where C : WithChildren<C> {
    /**
     * Children nodes.
     */
    val children: Collection<C>
}