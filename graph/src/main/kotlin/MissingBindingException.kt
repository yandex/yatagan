package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.NodeModel

class MissingBindingException constructor(nodeModel: NodeModel?) :
    IllegalStateException(nodeModel?.let { "missing binding for $nodeModel" } ?: "missing binding")