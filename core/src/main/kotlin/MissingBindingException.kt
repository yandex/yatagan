package com.yandex.dagger3.core

class MissingBindingException internal constructor(nodeModel: NodeModel?) :
    IllegalStateException(nodeModel?.let { "missing binding for $nodeModel" } ?: "missing binding")