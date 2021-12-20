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
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.core.lang.isKotlinObject
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.buildError

internal abstract class ModuleHostedBindingBase {
    protected abstract val impl: FunctionLangModel

    val scope: AnnotationLangModel? by lazy(LazyThreadSafetyMode.NONE) {
        impl.annotations.find(AnnotationLangModel::isScope)
    }

    val target: NodeModel by lazy(LazyThreadSafetyMode.NONE) {
        val type = if (multiBinding == MultiBindingKind.Flatten) {
            impl.returnType.typeArguments.firstOrNull() ?: impl.returnType
        } else impl.returnType
        NodeModelImpl(type = type, forQualifier = impl)
    }

    val multiBinding: MultiBindingKind?
        get() = impl.intoListAnnotationIfPresent?.let {
            if (it.flatten) MultiBindingKind.Flatten else MultiBindingKind.Direct
        }

    open fun validate(validator: Validator) {
        validator.child(target)
        if (multiBinding == MultiBindingKind.Flatten) {
            val firstArg = impl.returnType.typeArguments.firstOrNull()
            if (firstArg == null || !LangModelFactory.getCollectionType(firstArg).isAssignableFrom(impl.returnType)) {
                validator.report(buildError {
                    contents = "Flattening multi-binding must return Collection or any of its subtypes"
                })
            }
        }
        if (impl.returnType.isVoid) {
            validator.report(buildError {
                this.contents = "Binding method must not return ${impl.returnType}"
            })
        }
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

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (source in sources) {
            validator.child(source)
        }

        for (param in impl.parameters) {
            if (!impl.returnType.isAssignableFrom(param.type)) {
                validator.report(buildError {
                    contents = "$param is not compatible with binding return type ${impl.returnType}"
                })
            }
        }

        if (!impl.isAbstract) {
            validator.report(buildError { this.contents = "@Binds annotated method must be abstract" })
        }
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

    init {
        require(canRepresent(impl))
    }

    override val inputs: Sequence<NodeDependency> = impl.parameters.map { param ->
        NodeDependency(type = param.type, forQualifier = param)
    }.memoize()

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (dependency in inputs) {
            validator.child(dependency.node)
        }

        if (impl.isAbstract) {
            validator.report(buildError { this.contents = "@Provides-annotated method must not be abstract" })
        }
    }

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