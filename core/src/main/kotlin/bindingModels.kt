package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

/**
 * Declared in a [ModuleModel], and thus explicit, binding model.
 * Backed by a `lang`-level construct.
 */
sealed interface ModuleHostedBindingModel {
    val originModule: ModuleModel
    val target: NodeModel
    val isMultibinding: Boolean
}

/**
 * [com.yandex.daggerlite.Binds] binding model.
 */
interface BindsBindingModel : ModuleHostedBindingModel {
    val scope: AnnotationLangModel?
    val sources: Sequence<NodeModel>
}

/**
 * [com.yandex.daggerlite.Provides] binding model.
 */
interface ProvidesBindingModel : ModuleHostedBindingModel, ConditionalHoldingModel {
    val scope: AnnotationLangModel?
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
