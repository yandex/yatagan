package com.yandex.yatagan.core.graph

/**
 * A hierarchy trait, that allows accessing child nodes.
 */
public interface WithChildren<C> where C : WithChildren<C> {
    /**
     * Children nodes.
     */
    public val children: Collection<C>
}