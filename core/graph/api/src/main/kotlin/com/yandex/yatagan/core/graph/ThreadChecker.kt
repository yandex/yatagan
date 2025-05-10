package com.yandex.yatagan.core.graph

import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * An optional thread checker method to invoke when the provision in instantiated in Components that do not
 * require [synchronized access][BindingGraph.requiresSynchronizedAccess].
 */
public interface ThreadChecker : MayBeInvalid {
    /**
     * A *static* accessible method with no arguments named `assertThreadAccess`.
     * `null` if invalid, or if no thread checker was specified (default).
     */
    public val assertThreadAccessMethod: Method?
}