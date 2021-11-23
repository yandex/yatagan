package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel

/**
 * Represents a node in a Dagger Graph.
 * Each [NodeModel] can be *resolved* via appropriate [Binding] (See [BaseBinding.target]).
 * Basically, it's a type with some other information to fine tune resolution.
 */
interface NodeModel : ClassBackedModel {
    /**
     * Optional qualifier.
     * An opaque object representing additional qualifier information that can help to disambiguate nodes with the
     * same type.
     */
    val qualifier: AnnotationLangModel?

    /**
     * self-provision binding if supported by underlying type.
     */
    fun implicitBinding(forGraph: BindingGraph, forScope: AnnotationLangModel?): Binding?

    /**
     * TODO: doc.
     */
    val bootstrapInterfaces: Collection<BootstrapInterfaceModel>

}
