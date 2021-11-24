package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.LangModelFactory

/**
 * A [com.yandex.daggerlite.BootstrapInterface] annotated interface model.
 */
interface BootstrapInterfaceModel {
    /**
     * Yields node in a form of `@BootstrapList List<...>`.
     *
     * @see com.yandex.daggerlite.BootstrapList
     */
    fun asNode(factory: LangModelFactory): NodeModel
}
