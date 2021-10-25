package com.yandex.daggerlite.jap.lang

import java.io.Closeable
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private var utils: ProcessingUtils? = null

val Utils: ProcessingUtils get() = checkNotNull(utils)

class ProcessingUtils(
    val types: Types,
    val elements: Elements
) : Closeable {

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}