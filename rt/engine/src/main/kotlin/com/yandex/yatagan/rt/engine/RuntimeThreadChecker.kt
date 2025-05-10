package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.core.graph.ThreadChecker
import com.yandex.yatagan.lang.rt.kotlinObjectInstanceOrNull
import com.yandex.yatagan.lang.rt.rt

internal class RuntimeThreadChecker(
    threadChecker: ThreadChecker,
) {
    private val doAssert: (() -> Unit)? by lazy {
        threadChecker.assertThreadAccessMethod?.let { method ->
            val receiver = method.owner.kotlinObjectInstanceOrNull()
            val rt = method.rt
            { ->
                rt(receiver)
            }
        }
    }

    fun assertThreadAccess() {
        doAssert?.invoke()
    }
}