package com.yandex.daggerlite.core

/**
 * Trait for models, that can provide appropriate [NodeModel].
 */
interface HasNodeModel {
    /**
     * @return [NodeModel] representing the model.
     */
    fun asNode(): NodeModel
}