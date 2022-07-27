package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Declared in a [ModuleModel], and thus explicit, binding model.
 * Backed by a `lang`-level construct.
 */
interface ModuleHostedBindingModel : MayBeInvalid {
    val originModule: ModuleModel
    val target: BindingTargetModel
    val scopes: Set<AnnotationLangModel>
    val functionName: String

    sealed class BindingTargetModel {
        abstract val node: NodeModel

        override fun toString() = node.toString(childContext = null).toString()

        class Plain(
            override val node: NodeModel,
        ) : BindingTargetModel()

        class DirectMultiContribution(
            override val node: NodeModel,
        ) : BindingTargetModel()

        class FlattenMultiContribution(
            override val node: NodeModel,
            val flattened: NodeModel,
        ) : BindingTargetModel()

        class MappingContribution(
            override val node: NodeModel,
            val keyType: TypeLangModel,
            val keyValue: AnnotationLangModel.Value,
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
    val provision: FunctionLangModel
    val requiresModuleInstance: Boolean
}

