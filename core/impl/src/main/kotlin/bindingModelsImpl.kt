package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.IntoMap
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
    override val scopes: Set<AnnotationLangModel> by lazy {
        function.annotations.filter { it.isScope() }.toSet()
    }

    override val target: BindingTargetModel by lazy {
        if (function.returnType.isVoid) {
            BindingTargetModel.Plain(NodeModelImpl.Factory.VoidNode())
        } else {
            val target = NodeModelImpl(type = function.returnType, forQualifier = function)
            val intoList = function.intoListAnnotationIfPresent
            if (intoList != null) {
                if (intoList.flatten) {
                    BindingTargetModel.FlattenMultiContribution(
                        node = target,
                        flattened = NodeModelImpl(
                            type = function.returnType.typeArguments.firstOrNull() ?: function.returnType,
                            qualifier = target.qualifier,
                        )
                    )
                } else {
                    BindingTargetModel.DirectMultiContribution(node = target)
                }
            } else {
                if (function.isAnnotatedWith<IntoMap>()) {
                    val key = function.annotations.find { it.isMapKey() }
                    val annotationClass = key?.annotationClass
                    val valueAttribute = annotationClass?.attributes?.find { it.name == "value" }
                    val keyValue = valueAttribute?.let { key.getValue(valueAttribute) }
                    BindingTargetModel.MappingContribution(
                        node = target,
                        keyType = valueAttribute?.type,
                        keyValue = keyValue,
                        mapKeyClass = annotationClass,
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
                val firstArg = function.returnType.typeArguments.firstOrNull()
                if (firstArg == null || !LangModelFactory.getCollectionType(firstArg)
                        .isAssignableFrom(function.returnType)) {
                    validator.reportError(Errors.invalidFlatteningMultibinding(insteadOf = function.returnType))
                }
            }
            is BindingTargetModel.MappingContribution -> run {
                val keys = function.annotations.filter { it.isMapKey() }.toList()
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
    }
}

internal class BindsImpl(
    override val function: FunctionLangModel,
    override val originModule: ModuleModel,
) : BindsBindingModel, ModuleHostedBindingBase() {

    init {
        assert(canRepresent(function))
    }

    override val sources = function.parameters.map { parameter ->
        NodeModelImpl(type = parameter.type, forQualifier = parameter)
    }.memoize()

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (source in sources) {
            validator.child(source)
        }

        for (param in function.parameters) {
            if (!function.returnType.isAssignableFrom(param.type)) {
                validator.reportError(Errors.inconsistentBinds(
                    param = param.type,
                    returnType = function.returnType,
                ))
            }
        }

        if (!function.isAbstract) {
            validator.reportError(Errors.nonAbstractBinds())
        }
    }

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitBinds(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@binds",
        representation = {
            append("${originModule.type}::${function.name}(")
            when(childContext ?: Unit) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = function.returnType)
                }
                sources.singleOrNull() -> {  // alias
                    appendChildContextReference(reference = function.parameters.single())
                    append(")")
                }
                in sources -> {
                    val index = sources.indexOf(childContext)
                    append(".., ")
                    appendChildContextReference(reference = function.parameters.drop(index).first())
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
    override val function: FunctionLangModel,
    override val originModule: ModuleModelImpl,
) : ProvidesBindingModel,
    ModuleHostedBindingBase() {

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(checkNotNull(function.providesAnnotationIfPresent) { "Not reached" }.conditionals)
    }

    override val conditionals get() = conditionalsModel.conditionals

    override val inputs: List<NodeDependency> by lazy(PUBLICATION) {
        function.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.toList()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.child(conditionalsModel)

        for (dependency in inputs) {
            validator.child(dependency.node)
        }

        if (function.isAbstract) {
            validator.reportError(Errors.abstractProvides())
        }

        if (!function.isEffectivelyPublic) {
            validator.reportError(Errors.invalidAccessForProvides())
        }
    }

    override val requiresModuleInstance: Boolean
        get() = originModule.mayRequireInstance && !function.isStatic && !function.owner.isKotlinObject

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitProvides(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@provides",
        representation = {
            append("${originModule.type}::${function.name}(")
            when(childContext) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = function.returnType)
                }
                is NodeModel -> {
                    val index = inputs.indexOfFirst { it.node == childContext }
                    append(".., ")
                    appendChildContextReference(reference = function.parameters.drop(index).first())
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