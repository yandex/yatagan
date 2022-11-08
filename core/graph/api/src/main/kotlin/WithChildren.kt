package com.yandex.yatagan.core.graph

/**
 * A hierarchy trait, that allows accessing child nodes.
 */
interface WithChildren<C> where C : WithChildren<C> {
    /**
     * Children nodes.
     */
    val children: Collection<C>
}