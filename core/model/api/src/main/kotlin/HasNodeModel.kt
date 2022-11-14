package com.yandex.yatagan.core.model

/**
 * Trait for models, that can provide appropriate [NodeModel].
 * NOTE: The main requirement is that the model can be uniquely parsed from a node using
 * [NodeModel.getSpecificModel].
 */
public interface HasNodeModel : ClassBackedModel {
    /**
     *
     * Provides a [node][NodeModel] for this model.
     * Returned node's [superModel][NodeModel.getSpecificModel] is then yields `this`.
     *
     * @return [NodeModel] representing the model.
     */
    public fun asNode(): NodeModel

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitDefault(): R
        public fun visitComponent(model: ComponentModel): R = visitDefault()
        public fun visitComponentFactory(model: ComponentFactoryModel): R = visitDefault()
        public fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): R = visitDefault()
        public fun visitInjectConstructor(model: InjectConstructorModel): R = visitDefault()
    }
}