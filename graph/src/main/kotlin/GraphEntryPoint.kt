package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Graph-level abstraction over [com.yandex.daggerlite.core.ComponentModel.EntryPoint].
 */
interface GraphEntryPoint : MayBeInvalid {
    val getter: FunctionLangModel
    val dependency: NodeDependency
}