package com.yandex.yatagan.core.graph

/**
 * A hierarchy trait, that allows accessing optional parent nodes.
 */
public interface WithParents<P> where P : WithParents<P> {
    /**
     * Parent node. `null` if root is reached.
     */
    public val parent: P?
}