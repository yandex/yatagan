package com.yandex.yatagan.testing.doc_testing

import org.jetbrains.dokka.model.*

/**
 * A stack of [Documentable] that denotes current transforming context. Immutable.
 */
@JvmInline
value class TransformContext private constructor(
    val stack: List<Documentable>,
) {
    /**
     * Constructs root context from a [DModule] documentable.
     */
    constructor(root: DModule) : this(listOf(root))

    /**
     * Constructs child context from `this` and provided [context] documentable.
     *
     * @param context a documentable to append to the resulting context.
     */
    fun TransformContext(context: Documentable): TransformContext = TransformContext(stack + context)
}