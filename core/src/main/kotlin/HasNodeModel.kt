package com.yandex.daggerlite.core

/**
 * Trait for models, that can provide appropriate [NodeModel].
 * NOTE: The main requirement is that the model can be uniquely parsed from a node using
 * [NodeModel.getSpecificModel].
 */
interface HasNodeModel : ClassBackedModel {
    /**
     *
     * Provides a [node][NodeModel] for this model.
     * Returned node's [superModel][NodeModel.getSpecificModel] is then yields `this`.
     *
     * @return [NodeModel] representing the model.
     */
    fun asNode(): NodeModel

    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitDefault(): R
        fun visitComponent(model: ComponentModel): R = visitDefault()
        fun visitComponentFactory(model: ComponentFactoryModel): R = visitDefault()
        fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): R = visitDefault()
        fun visitInjectConstructor(model: InjectConstructorModel): R = visitDefault()
    }
}