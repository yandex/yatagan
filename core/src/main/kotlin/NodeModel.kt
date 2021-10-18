package com.yandex.dagger3.core

/**
 * Represents a node in a Dagger Graph.
 * Each [NodeModel] can be *resolved* via appropriate [Binding] (See [Binding.target]).
 * Basically, it's a type with some other information to fine tune resolution.
 *
 * Implementations must provide stable [equals]/[hashCode] implementation for correct matching.
 */
abstract class NodeModel : ClassBackedModel {
    /**
     * Optional [Qualifier].
     */
    abstract val qualifier: Qualifier?

    /**
     * self-provision binding if supported by underlying type.
     * TODO: rename(jeffset): rename to implicitBinding.
     */
    abstract val defaultBinding: Binding?  // TODO(jeffset): use creating function instead of property

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

    final override fun equals(other: Any?): Boolean {
        // FIXME: This is way too expensive for an RT implementation; allow RT to customize this behavior.
        if (this === other) return true

        other as NodeModel

        if (qualifier != other.qualifier) return false
        if (name != other.name) return false

        return true
    }

    final override fun hashCode(): Int {
        var result = qualifier?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        return result
    }
}