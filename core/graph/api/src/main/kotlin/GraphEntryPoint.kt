package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Graph-level abstraction over [com.yandex.yatagan.core.ComponentModel.EntryPoint].
 */
public interface GraphEntryPoint : MayBeInvalid {
    public val getter: Method
    public val dependency: NodeDependency
}