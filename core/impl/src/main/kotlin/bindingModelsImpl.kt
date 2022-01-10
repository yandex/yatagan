package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
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
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
import kotlin.LazyThreadSafetyMode.NONE

internal abstract class ModuleHostedBindingBase : ModuleHostedBindingModel {
    protected abstract val impl: FunctionLangModel

    override val scope: AnnotationLangModel? by lazy(NONE) {
        impl.annotations.find(AnnotationLangModel::isScope)
    }

    override val target: NodeModel by lazy(NONE) {
        val type = if (multiBinding == MultiBindingKind.Flatten) {
            impl.returnType.typeArguments.firstOrNull() ?: impl.returnType
        } else impl.returnType
        if (impl.returnType.isVoid) {
            NodeModelImpl.Factory.NoNode()
        } else NodeModelImpl(type = type, forQualifier = impl)
    }

    override val multiBinding: MultiBindingKind?
        get() = impl.intoListAnnotationIfPresent?.let {
            if (it.flatten) MultiBindingKind.Flatten else MultiBindingKind.Direct
        }

    override fun validate(validator: Validator) {
        validator.child(target)
        if (multiBinding == MultiBindingKind.Flatten) {
            val firstArg = impl.returnType.typeArguments.firstOrNull()
            if (firstArg == null || !LangModelFactory.getCollectionType(firstArg).isAssignableFrom(impl.returnType)) {
                validator.reportError(Errors.`invalid flattening multibinding`(insteadOf = impl.returnType))
            }
        }
        if (impl.returnType.isVoid) {
            validator.reportError(Errors.`binding must not return void`())
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
                validator.reportError(Errors.`binds param type is incompatible with return type`(
                    param = param.type,
                    returnType = impl.returnType,
                ))
            }
        }

        if (!impl.isAbstract) {
            validator.reportError(Errors.`binds must be abstract`())
        }
    }

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitBinds(this)
    }

    override fun toString(): String {
        return "@Binds $originModule::${impl.name}(${sources.joinToString()}): $target"
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
    ModuleHostedBindingBase() {

    private val conditionalHolder = ConditionalHoldingModelImpl(
        checkNotNull(impl.providesAnnotationIfPresent) { "Not reached" }.conditionals)

    override val conditionals get() = conditionalHolder.conditionals

    private val paramsToDependencies by lazy(NONE) {
        impl.parameters.associateWith { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }
    }

    override val inputs: Sequence<NodeDependency>
        get() = paramsToDependencies.values.asSequence()

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.inline(conditionalHolder)

        for (dependency in inputs) {
            validator.child(dependency.node)
        }

        if (impl.isAbstract) {
            validator.reportError(Errors.`provides must not be abstract`())
        }
    }

    override val provision: FunctionLangModel
        get() = impl

    override val requiresModuleInstance: Boolean
        get() = originModule.mayRequireInstance && !impl.isStatic && !impl.owner.isKotlinObject

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitProvides(this)
    }

    override fun toString(): String {
        return "@Provides $originModule::${impl.name}(${inputs.joinToString()}): $target"
    }

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.providesAnnotationIfPresent != null
        }
    }
}