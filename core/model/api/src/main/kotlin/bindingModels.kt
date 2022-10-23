package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Declared in a [ModuleModel], and thus explicit, binding model.
 * Backed by a `lang`-level construct.
 */
interface ModuleHostedBindingModel : MayBeInvalid {
    /**
     * Module the binding originates from.
     */
    val originModule: ModuleModel

    /**
     * Parsed return value of the binding. See [BindingTargetModel] for the details.
     */
    val target: BindingTargetModel

    /**
     * Declared scope annotations.
     */
    val scopes: Set<Annotation>

    /**
     * Underlying method model.
     *
     * Use only for extracting the info not available via the API of the model.
     */
    val method: Method

    /**
     * Binding target variants.
     */
    sealed class BindingTargetModel {
        /**
         * A node corresponding to the return type of the binding model.
         * Doesn't always directly correspond to the effective binding target - multi-bindings can be in effect.
         */
        abstract val node: NodeModel

        override fun toString() = node.toString(childContext = null).toString()

        /**
         * Binding for the plain [node].
         */
        class Plain(
            override val node: NodeModel,
        ) : BindingTargetModel()

        /**
         * Single element list contribution to the List of [node]s.
         */
        class DirectMultiContribution(
            override val node: NodeModel,
            val kind: CollectionTargetKind,
        ) : BindingTargetModel()

        /**
         * Collection contribution to the List of [flattened], which is an unwrapped [node].
         */
        class FlattenMultiContribution(
            override val node: NodeModel,

            /**
             * An unwrapped [node] (its type argument, given [node] is a collection).
             */
            val flattened: NodeModel,

            /**
             * Collection kind.
             */
            val kind: CollectionTargetKind,
        ) : BindingTargetModel()

        /**
         * Contribution of a single [node] as a value to a map with the [keyType] under [keyValue].
         */
        class MappingContribution(
            override val node: NodeModel,

            /**
             * Mapping key type. `null` when not-found/unresolved.
             */
            val keyType: Type?,

            /**
             * Mapping key value. `null` when not-found/unresolved.
             */
            val keyValue: Annotation.Value?,

            /**
             * Annotation class of the Map-key annotation. `null` when not-found/unresolved.
             */
            val mapKeyClass: AnnotationDeclarationLangModel?,
        ) : BindingTargetModel()
    }

    interface Visitor<R> {
        fun visitBinds(model: BindsBindingModel): R
        fun visitProvides(model: ProvidesBindingModel): R
    }

    fun <R> accept(visitor: Visitor<R>): R
}

/**
 * [com.yandex.daggerlite.Binds] binding model.
 */
interface BindsBindingModel : ModuleHostedBindingModel {
    val sources: Sequence<NodeModel>
}

/**
 * [com.yandex.daggerlite.Provides] binding model.
 */
interface ProvidesBindingModel : ModuleHostedBindingModel, ConditionalHoldingModel {
    val inputs: List<NodeDependency>
    val requiresModuleInstance: Boolean
}

