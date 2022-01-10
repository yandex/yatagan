package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Declared in a [ModuleModel], and thus explicit, binding model.
 * Backed by a `lang`-level construct.
 */
interface ModuleHostedBindingModel : MayBeInvalid {
    val originModule: ModuleModel
    val target: NodeModel
    val multiBinding: MultiBindingKind?
    val scope: AnnotationLangModel?

    enum class MultiBindingKind {
        Direct,
        Flatten,
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
    val inputs: Sequence<NodeDependency>
    val provision: FunctionLangModel
    val requiresModuleInstance: Boolean
}

/**
 * [javax.inject.Inject]-annotated constructor binding model.
 */
interface InjectConstructorBindingModel : ConditionalHoldingModel {
    val target: NodeModel
    val constructor: ConstructorLangModel
    val scope: AnnotationLangModel?
    val inputs: Sequence<NodeDependency>
}
