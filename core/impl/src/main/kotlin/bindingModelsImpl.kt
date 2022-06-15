package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
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
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal abstract class ModuleHostedBindingBase : ModuleHostedBindingModel {
    protected abstract val impl: FunctionLangModel

    override val scope: AnnotationLangModel? by lazy(PUBLICATION) {
        impl.annotations.find(AnnotationLangModel::isScope)
    }

    override val target: BindingTargetModel by lazy(PUBLICATION) {
        if (impl.returnType.isVoid) {
            BindingTargetModel.Plain(NodeModelImpl.Factory.NoNode())
        } else {
            val target = NodeModelImpl(type = impl.returnType, forQualifier = impl)
            val intoList = impl.intoListAnnotationIfPresent
            if (intoList != null) {
                if (intoList.flatten) {
                    BindingTargetModel.FlattenMultiContribution(
                        node = target,
                        flattened = NodeModelImpl(
                            type = impl.returnType.typeArguments.firstOrNull() ?: impl.returnType,
                            qualifier = target.qualifier,
                        )
                    )
                } else {
                    BindingTargetModel.DirectMultiContribution(node = target)
                }
            } else {
                BindingTargetModel.Plain(node = target)
            }
        }
    }

    override fun validate(validator: Validator) {
        validator.child(target.node)
        if (target is BindingTargetModel.FlattenMultiContribution) {
            val firstArg = impl.returnType.typeArguments.firstOrNull()
            if (firstArg == null || !LangModelFactory.getCollectionType(firstArg).isAssignableFrom(impl.returnType)) {
                validator.reportError(Errors.invalidFlatteningMultibinding(insteadOf = impl.returnType))
            }
        }
        if (impl.returnType.isVoid) {
            validator.reportError(Errors.voidBinding())
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
    }.memoize()

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (source in sources) {
            validator.child(source)
        }

        for (param in impl.parameters) {
            if (!impl.returnType.isAssignableFrom(param.type)) {
                validator.reportError(Errors.inconsistentBinds(
                    param = param.type,
                    returnType = impl.returnType,
                ))
            }
        }

        if (!impl.isAbstract) {
            validator.reportError(Errors.nonAbstractBinds())
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

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(checkNotNull(impl.providesAnnotationIfPresent) { "Not reached" }.conditionals)
    }

    override val conditionals get() = conditionalsModel.conditionals

    override val inputs: List<NodeDependency> by lazy(NONE) {
        impl.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.toList()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.inline(conditionalsModel)

        for (dependency in inputs) {
            validator.child(dependency.node)
        }

        if (impl.isAbstract) {
            validator.reportError(Errors.abstractProvides())
        }

        if (!impl.isEffectivelyPublic) {
            validator.reportError(Errors.invalidAccessForProvides())
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