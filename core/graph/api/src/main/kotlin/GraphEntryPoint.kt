package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Graph-level abstraction over [com.yandex.yatagan.core.ComponentModel.EntryPoint].
 */
interface GraphEntryPoint : MayBeInvalid {
    val getter: Method
    val dependency: NodeDependency
}