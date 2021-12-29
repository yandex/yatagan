package com.yandex.daggerlite.core

/**
 * Trait for models, that can provide appropriate [NodeModel].
 */
interface HasNodeModel : ClassBackedModel {
    /**
     * @return [NodeModel] representing the model.
     */
    fun asNode(): NodeModel
}