package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.IntoMap
import com.yandex.daggerlite.base.filterIntoSmallSet
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.AnnotationValueVisitorAdapter
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.core.lang.isKotlinObject
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings.Errors
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal abstract class ModuleHostedBindingBase : ModuleHostedBindingModel {
    protected abstract val impl: FunctionLangModel

    override val scopes: Set<AnnotationLangModel> by lazy {
        impl.annotations.filterIntoSmallSet { it.isScope() }
    }

    override val functionName: String
        get() = impl.name

    override val target: BindingTargetModel by lazy {
        if (impl.returnType.isVoid) {
            BindingTargetModel.Plain(NodeModelImpl.Factory.VoidNode())
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
                if (impl.isAnnotatedWith<IntoMap>()) {
                    val key = impl.annotations.find { it.isMapKey() }
                    val valueAttribute = key?.annotationClass?.attributes?.find { it.name == "value" }
                    val keyValue = valueAttribute?.let { key.getValue(valueAttribute) } ?: InvalidValue()
                    BindingTargetModel.MappingContribution(
                        node = target,
                        keyType = valueAttribute?.type ?: LangModelFactory.errorType,
                        keyValue = keyValue,
                    )
                } else {
                    BindingTargetModel.Plain(node = target)
                }
            }
        }
    }

    override fun validate(validator: Validator) {
        validator.child(target.node)
        when (target) {
            is BindingTargetModel.FlattenMultiContribution -> {
                val firstArg = impl.returnType.typeArguments.firstOrNull()
                if (firstArg == null || !LangModelFactory.getCollectionType(firstArg).isAssignableFrom(impl.returnType)) {
                    validator.reportError(Errors.invalidFlatteningMultibinding(insteadOf = impl.returnType))
                }
            }
            is BindingTargetModel.MappingContribution -> run {
                val keys = impl.annotations.filter { it.isMapKey() }.toList()
                if (keys.size != 1) {
                    validator.reportError(if (keys.isEmpty()) Errors.missingMapKey() else Errors.multipleMapKeys())
                    return@run
                }
                val key = keys.first()
                val clazz = key.annotationClass
                val valueAttribute = clazz.attributes.find { it.name == "value" }
                if (valueAttribute == null) {
                    validator.reportError(Errors.missingMapKeyValue(annotationClass = clazz))
                    return@run
                }
                key.getValue(valueAttribute).accept(object : AnnotationValueVisitorAdapter<Unit>() {
                    // Unresolved is not reported here, as it's [:lang]'s problem and will be reported by the
                    //  compiler anyway.
                    override fun visitDefault() = Unit
                    override fun visitAnnotation(value: AnnotationLangModel) {
                        validator.reportError(Errors.unsupportedAnnotationValueAsMapKey(annotationClass = clazz))
                    }
                    override fun visitArray(value: List<AnnotationLangModel.Value>) {
                        validator.reportError(Errors.unsupportedArrayValueAsMapKey(annotationClass = clazz))
                    }
                })
            }
            is BindingTargetModel.DirectMultiContribution, is BindingTargetModel.Plain -> { /*Nothing to validate*/ }
        }
//        if (impl.returnType.isVoid) {
//            validator.reportError(Errors.voidBinding())
//        }
    }

    private class InvalidValue : AnnotationLangModel.Value {
        override fun <R> accept(visitor: AnnotationLangModel.Value.Visitor<R>): R = visitor.visitUnresolved()
        override val platformModel: Nothing? get() = null
        override fun toString() = "<undefined>"
    }
}

internal class BindsImpl(
    override val impl: FunctionLangModel,
    override val originModule: ModuleModel,
) : BindsBindingModel, ModuleHostedBindingBase() {

    init {
        assert(canRepresent(impl))
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

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@binds",
        representation = {
            append("${originModule.type}::${impl.name}(")
            when(childContext ?: Unit) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = impl.returnType)
                }
                sources.singleOrNull() -> {  // alias
                    appendChildContextReference(reference = impl.parameters.single())
                    append(")")
                }
                in sources -> {
                    val index = sources.indexOf(childContext)
                    append(".., ")
                    appendChildContextReference(reference = impl.parameters.drop(index).first())
                    append(", ..)")
                }
                else -> {
                    append("...)")
                }
            }
        },
    )

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

    override val inputs: List<NodeDependency> by lazy(PUBLICATION) {
        impl.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.toList()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.child(conditionalsModel)

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

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@provides",
        representation = {
            append("${originModule.type}::${impl.name}(")
            when(childContext) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = impl.returnType)
                }
                is NodeModel -> {
                    val index = inputs.indexOfFirst { it.node == childContext }
                    append(".., ")
                    appendChildContextReference(reference = impl.parameters.drop(index).first())
                    append(", ..)")
                }
                else -> {
                    append("...)")
                }
            }
        },
    )

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.providesAnnotationIfPresent != null
        }
    }
}