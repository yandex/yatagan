package com.yandex.dagger3.core

/**
 * Represents a node in a Dagger Graph.
 * Each [NodeModel] can be *resolved* via appropriate [Binding] (See [Binding.target]).
 * Basically, it's a type with some other information to fine tune resolution.
 *
 * Implementations must provide stable [equals]/[hashCode] implementation for correct matching.
 */
interface NodeModel : ClassBackedModel {
    val qualifier: Qualifier?
    val defaultBinding: Binding?

    /**
     * An opaque object representing additional qualifier information that can help to disambiguate nodes with the
     * same type.
     * Must provide stable [equals]/[hashCode] implementation.
     */
    interface Qualifier

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
             * [dagger.Lazy]-wrapped type is requested.
             */
            Lazy,

            /**
             * [javax.inject.Provider]-wrapped type is requested.
             */
            Provider,
        }

        override fun toString() = "$node [$kind]"
    }
}