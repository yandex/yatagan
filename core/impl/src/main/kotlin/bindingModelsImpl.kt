package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.MultiBindingKind
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.core.lang.isKotlinObject

internal abstract class ModuleHostedBindingBase {
    protected abstract val impl: FunctionLangModel

    val scope: AnnotationLangModel? by lazy(LazyThreadSafetyMode.NONE) {
        impl.annotations.find(AnnotationLangModel::isScope)
    }

    val target: NodeModel by lazy(LazyThreadSafetyMode.NONE) {
        val type = if (impl.intoListAnnotationIfPresent?.flatten == true) {
            impl.returnType.typeArguments.first()
        } else impl.returnType
        NodeModelImpl(type = type, forQualifier = impl)
    }

    val multiBinding: MultiBindingKind?
        get() = impl.intoListAnnotationIfPresent?.let {
            if (it.flatten) MultiBindingKind.Flatten else MultiBindingKind.Direct
        }
}

internal class BindsImpl(
    override val impl: FunctionLangModel,
    override val originModule: ModuleModel,
) : BindsBindingModel, ModuleHostedBindingBase() {

    init {
        require(canRepresent(impl))
    }

    override val sources = impl.parameters.map { parameter ->
        NodeModelImpl(type = parameter.type, forQualifier = parameter)
    }

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.isAnnotatedWith<Binds>()
        }
    }
}

internal class ProvidesImpl(
    override val impl: FunctionLangModel,
    override val originModule: ModuleModelImpl,
) : ProvidesBindingModel,
    ModuleHostedBindingBase(),
    ConditionalHoldingModel by ConditionalHoldingModelImpl(impl.providesAnnotationIfPresent!!.conditionals) {

    override val inputs: Sequence<NodeDependency> = impl.parameters.map { param ->
        NodeDependency(type = param.type, forQualifier = param)
    }.memoize()

    override val provision: FunctionLangModel
        get() = impl

    override val requiresModuleInstance: Boolean
        get() = originModule.mayRequireInstance && !impl.isStatic && !impl.owner.isKotlinObject

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.providesAnnotationIfPresent != null
        }
    }
}