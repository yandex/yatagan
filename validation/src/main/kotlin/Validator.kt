package com.yandex.daggerlite.validation

/**
 * A visitor-like object that validates [MayBeInvalid].
 */
interface Validator {
    /**
     * Reports given message for a [MayBeInvalid] node that is currently being visited.
     * Paths on which the message occurs will be grouped.
     */
    fun report(message: ValidationMessage)

    /**
     * Adds a child node to a validation graph.
     */
    fun child(node: MayBeInvalid)

    /**
     * Performs validation of the [node] "inline": all the reported messages will be associated with a current node,
     * not the given [node]. Moreover, [node] will never show on any path.
     *
     * Useful on objects that doesn't resemble any public entity but rather an implementation detail of a complex
     * entity.
     */
    fun inline(node: MayBeInvalid)
}