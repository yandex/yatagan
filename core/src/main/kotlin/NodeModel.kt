package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.NodeModel.Dependency.Kind
import com.yandex.daggerlite.core.lang.AnnotationLangModel

/**
 * Represents a node in a Dagger Graph.
 * Each [NodeModel] can be *resolved* via appropriate [Binding] (See [BaseBinding.target]).
 * Basically, it's a type with some other information to fine tune resolution.
 *
 * Implementations must provide stable [equals]/[hashCode] implementation for correct matching.
 */
abstract class NodeModel : ClassBackedModel() {
    /**
     * Optional qualifier.
     * An opaque object representing additional qualifier information that can help to disambiguate nodes with the
     * same type.
     */
    abstract val qualifier: AnnotationLangModel?

    /**
     * self-provision binding if supported by underlying type.
     */
    abstract fun implicitBinding(): Binding?

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
        }

        override fun toString() = "$node [$kind]"
    }

    final override fun equals(other: Any?): Boolean {
        return this === other || other is NodeModel && (type == other.type && qualifier == other.qualifier)
    }

    final override fun hashCode(): Int {
        var result = qualifier?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString() = buildString {
        qualifier?.let {
            append(qualifier)
            append(' ')
        }
        append(type)
    }
}
