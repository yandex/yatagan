package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.core.graph.bindings.Binding

interface BindingAccessDelegate {
    fun createBinding(binding: Binding): Any
    fun assertThreadAccessIfNeeded()
}