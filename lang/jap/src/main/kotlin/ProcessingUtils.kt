package com.yandex.daggerlite.jap.lang

import java.io.Closeable
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private var utils: ProcessingUtils? = null

internal val Utils: ProcessingUtils get() = checkNotNull(utils) {
    "Not reached: utils are used before set/after cleared"
}

class ProcessingUtils(
    val types: Types,
    val elements: Elements,
) : Closeable {
    val booleanType: TypeElement by lazy {
        elements.getTypeElement("java.lang.Boolean")
    }
    val objectType : TypeElement by lazy {
        elements.getTypeElement("java.lang.Object")
    }
    val stringType : TypeElement by lazy {
        elements.getTypeElement("java.lang.String")
    }

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}