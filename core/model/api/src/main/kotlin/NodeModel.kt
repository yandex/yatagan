package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents a node in a Dagger Graph, that can be resolved.
 * Basically, it's a type with some other information to fine tune resolution.
 *
 * The main properties that resemble a node are [type] and [qualifier].
 *
 * The rest functions/properties are built around those two and do not add any new information.
 *
 * [NodeModel] can be used as [NodeDependency] directly to avoid additional allocations,
 * if the requested [DependencyKind] is [Direct][DependencyKind.Direct].
 */
interface NodeModel : ClassBackedModel, MayBeInvalid, Comparable<NodeModel>, NodeDependency {
    /**
     * Optional qualifier.
     * An opaque object representing additional qualifier information that can help to disambiguate nodes with the
     * same type.
     */
    val qualifier: Annotation?

    /**
     * Provides all nodes that must be bound to a list-binding of this node.
     */
    fun multiBoundListNodes(): Array<NodeModel>

    /**
     * Provides all nodes that must be bound to a set-binding of this node.
     */
    fun multiBoundSetNodes(): Array<NodeModel>

    /**
     * Provides all nodes that must be bound to a mapping of [key] to this node.
     *
     * @param key a type to use as Map's key type.
     * @param asProviders whether wrap this type into `javax.inject.Provider` as Map's value.
     */
    fun multiBoundMapNodes(key: Type, asProviders: Boolean): Array<NodeModel>

    /**
     * What specific core-level model the node represents. `null` if none.
     * Use [HasNodeModel.accept] to determine which specific model it is.
     */
    fun getSpecificModel(): HasNodeModel?

    /**
     * Returns a node that is equal to this one without qualifier.
     *
     * @return the same node only with [qualifier] = `null`.
     * If `this` node already has no qualifier, `this` is returned.
     */
    fun dropQualifier(): NodeModel

    /**
     * `true` if such node can not be satisfied by definition for whatever reason, e.g. framework type.
     * `false` for any normal node.
     */
    val hintIsFrameworkType: Boolean

    /**
     * [NodeDependency] compatibility function.
     *
     * Always equal to `this`.
     */
    override val node: NodeModel

    /**
     * [NodeDependency] compatibility function.
     *
     * Always equal [DependencyKind.Direct].
     */
    override val kind: DependencyKind

    /**
     * [NodeDependency] compatibility function.
     *
     * @return Always provided [node].
     */
    override fun copyDependency(node: NodeModel, kind: DependencyKind): NodeDependency
}
