package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.NodeModel.Dependency.Kind
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
    fun implicitBinding(forGraph: BindingGraph): Binding?

    /**
     * A [NodeModel] with a request [Kind].
     */
    data class Dependency(
        val node: NodeModel,
        val kind: Kind = Kind.Direct,
    ) {
        enum class Kind {
            /**
             * Type is requested directly.
             */
            Direct,

            /**
             * [com.yandex.daggerlite.Lazy]-wrapped type is requested.
             */
            Lazy,

            /**
             * [javax.inject.Provider]-wrapped type is requested.
             */
            Provider,

            /**
             * [com.yandex.daggerlite.Optional]-wrapped type is requested.
             */
            Optional,

            /**
             * TODO: doc.
             */
            OptionalLazy,

            /**
             * TODO: doc.
             */
            OptionalProvider,
        }

        override fun toString() = "$node [$kind]"
    }
}
