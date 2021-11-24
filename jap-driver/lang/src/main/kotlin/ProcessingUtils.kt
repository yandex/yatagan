package com.yandex.daggerlite.jap.lang

import java.io.Closeable
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private var utils: ProcessingUtils? = null

val Utils: ProcessingUtils get() = checkNotNull(utils)

class ProcessingUtils(
    val types: Types,
    val elements: Elements
) : Closeable {
    val booleanType: TypeElement by lazy {
        elements.getTypeElement("java.lang.Boolean")
    }
    val voidType: TypeElement by lazy {
        elements.getTypeElement("java.lang.Void")
    }

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}