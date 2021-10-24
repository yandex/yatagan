package com.yandex.daggerlite.core

class MissingBindingException constructor(nodeModel: NodeModel?) :
    IllegalStateException(nodeModel?.let { "missing binding for $nodeModel" } ?: "missing binding")