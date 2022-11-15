package com.yandex.yatagan.validation

/**
 * A visitor-like object that validates [MayBeInvalid].
 */
public interface Validator {
    /**
     * Reports given message for a [MayBeInvalid] node that is currently being visited.
     * Paths on which the message occurs will be grouped.
     */
    public fun report(message: ValidationMessage)

    /**
     * Adds a child node to a validation graph.
     *
     * @see inline
     */
    public fun child(node: MayBeInvalid)

    /**
     * Similar to [child], yet
     * performs validation of the [node] "inline": all the reported messages will be associated with a current node,
     * not with the given [node]. Moreover, [node]'s [toString] will never show on any path.
     *
     * Useful on objects that doesn't resemble any public entity but rather an implementation detail of a complex
     * entity.
     */
    public fun inline(node: MayBeInvalid)
}