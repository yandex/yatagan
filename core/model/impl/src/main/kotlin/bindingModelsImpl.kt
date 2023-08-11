/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.BindsBindingModel
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.ModuleHostedBindingModel
import com.yandex.yatagan.core.model.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ProvidesBindingModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationValueVisitorAdapter
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.getCollectionType
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings.Errors
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal abstract class ModuleHostedBindingBase : ModuleHostedBindingModel {
    override val scopes: Set<ScopeModel> by lazy {
        buildScopeModels(method)
    }

    override val target: BindingTargetModel by lazy {
        if (method.returnType.isVoid) {
            BindingTargetModel.Plain(NodeModelImpl.Factory.VoidNode())
        } else {
            val target = NodeModelImpl(type = method.returnType, forQualifier = method)
            val intoCollection = method.getAnnotations(BuiltinAnnotation.IntoCollectionFamily).firstOrNull()
            when {
                intoCollection is BuiltinAnnotation.IntoCollectionFamily.IntoList -> computeMultiContributionTarget(
                    intoCollection = intoCollection,
                    kind = CollectionTargetKind.List,
                )

                intoCollection is BuiltinAnnotation.IntoCollectionFamily.IntoSet -> computeMultiContributionTarget(
                    intoCollection = intoCollection,
                    kind = CollectionTargetKind.Set,
                )

                method.getAnnotation(BuiltinAnnotation.IntoMap) != null -> {
                    val key = method.annotations.find { it.isMapKey() }
                    val annotationClass = key?.annotationClass
                    val valueAttribute = annotationClass?.attributes?.singleOrNull()
                    val keyValue = valueAttribute?.let { key.getValue(valueAttribute) }
                    BindingTargetModel.MappingContribution(
                        node = target,
                        keyType = valueAttribute?.type,
                        keyValue = keyValue,
                        mapKeyClass = annotationClass,
                    )
                }

                else -> BindingTargetModel.Plain(node = target)
            }
        }
    }

    override fun validate(validator: Validator) {
        validator.child(target.node)

        if (method.getAnnotations(BuiltinAnnotation.IntoCollectionFamily).size > 1) {
            validator.reportError(Errors.conflictingCollectionBindingAnnotations())
        }

        when (target) {
            is BindingTargetModel.FlattenMultiContribution -> {
                val firstArg = method.returnType.typeArguments.firstOrNull()
                if (firstArg == null || !LangModelFactory.getCollectionType(firstArg)
                        .isAssignableFrom(method.returnType)) {
                    validator.reportError(Errors.invalidFlatteningMultibinding(insteadOf = method.returnType))
                }
            }
            is BindingTargetModel.MappingContribution -> run {
                val keys = method.annotations.filter { it.isMapKey() }.toList()
                if (keys.size != 1) {
                    validator.reportError(if (keys.isEmpty()) Errors.missingMapKey() else Errors.multipleMapKeys())
                    return@run
                }
                val key = keys.first()
                val clazz = key.annotationClass
                val valueAttribute = clazz.attributes.singleOrNull()
                if (valueAttribute == null) {
                    validator.reportError(Errors.missingMapKeyValue(annotationClass = clazz))
                    return@run
                }
                key.getValue(valueAttribute).accept(object : AnnotationValueVisitorAdapter<Unit>() {
                    // Unresolved is not reported here, as it's [:lang]'s problem and will be reported by the
                    //  compiler anyway.
                    override fun visitDefault() = Unit
                    override fun visitAnnotation(value: Annotation) {
                        validator.reportError(Errors.unsupportedAnnotationValueAsMapKey(annotationClass = clazz))
                    }
                    override fun visitArray(value: List<Annotation.Value>) {
                        validator.reportError(Errors.unsupportedArrayValueAsMapKey(annotationClass = clazz))
                    }
                })
            }
            is BindingTargetModel.DirectMultiContribution, is BindingTargetModel.Plain -> { /*Nothing to validate*/ }
        }
    }

    private fun computeMultiContributionTarget(
        intoCollection: BuiltinAnnotation.IntoCollectionFamily,
        kind: CollectionTargetKind,
    ) : BindingTargetModel {
        val target = NodeModelImpl(type = method.returnType, forQualifier = method)
        return if (intoCollection.flatten) {
            BindingTargetModel.FlattenMultiContribution(
                node = target,
                flattened = NodeModelImpl(
                    type = method.returnType.typeArguments.firstOrNull() ?: method.returnType,
                    qualifier = target.qualifier,
                ),
                kind = kind,
            )
        } else BindingTargetModel.DirectMultiContribution(
            node = target,
            kind = kind,
        )
    }
}

internal class BindsImpl(
    override val method: Method,
    override val originModule: ModuleModel,
) : BindsBindingModel, ModuleHostedBindingBase() {

    init {
        assert(canRepresent(method))
    }

    override val sources by lazy {
        method.parameters.mapTo(mutableListOf()) { parameter ->
            NodeModelImpl(type = parameter.type, forQualifier = parameter)
        }
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (source in sources) {
            validator.child(source)
        }

        for (param in method.parameters) {
            if (!method.returnType.isAssignableFrom(param.type)) {
                validator.reportError(Errors.inconsistentBinds(
                    param = param.type,
                    returnType = method.returnType,
                ))
            }
        }

        if (!method.isAbstract) {
            validator.reportError(Errors.nonAbstractBinds())
        }
    }

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitBinds(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@binds",
        representation = {
            append("${originModule.type}::${method.name}(")
            when(childContext ?: Unit) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = method.returnType)
                }
                sources.singleOrNull() -> {  // alias
                    appendChildContextReference(reference = method.parameters.single())
                    append(")")
                }
                in sources -> {
                    val index = sources.indexOf(childContext)
                    append(".., ")
                    appendChildContextReference(reference = method.parameters.drop(index).first())
                    append(", ..)")
                }
                else -> {
                    append("...)")
                }
            }
        },
    )

    companion object {
        fun canRepresent(method: Method): Boolean {
            return method.getAnnotation(BuiltinAnnotation.Binds) != null
        }
    }
}

internal class ProvidesImpl(
    override val method: Method,
    override val originModule: ModuleModelImpl,
) : ProvidesBindingModel,
    ModuleHostedBindingBase() {

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(
            checkNotNull(method.getAnnotation(BuiltinAnnotation.Provides)) { "Not reached" }.conditionals
        )
    }

    override val conditionals: List<ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel> get() = conditionalsModel.conditionals

    override val inputs: List<NodeDependency> by lazy(PUBLICATION) {
        method.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.toList()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.child(conditionalsModel)

        for (dependency in inputs) {
            validator.child(dependency.node)
        }

        if (method.isAbstract) {
            validator.reportError(Errors.abstractProvides())
        }

        if (!method.isEffectivelyPublic) {
            validator.reportError(Errors.invalidAccessForProvides())
        }

        if (requiresModuleInstance && !originModule.mayRequireInstance) {
            validator.reportError(Errors.nonStaticProvidesInAbstractModule())
        }
    }

    override val requiresModuleInstance: Boolean
        get() = !method.isAbstract && !method.isStatic && !method.owner.isKotlinObject

    override fun <R> accept(visitor: ModuleHostedBindingModel.Visitor<R>): R {
        return visitor.visitProvides(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "@provides",
        representation = {
            append("${originModule.type}::${method.name}(")
            when(childContext) {
                target.node -> {  // return type
                    append("): ")
                    appendChildContextReference(reference = method.returnType)
                }
                is NodeModel -> {
                    val index = inputs.indexOfFirst { it.node == childContext }
                    append(".., ")
                    appendChildContextReference(reference = method.parameters.drop(index).first())
                    append(", ..)")
                }
                else -> {
                    append("...)")
                }
            }
        },
    )

    companion object {
        fun canRepresent(method: Method): Boolean {
            return method.getAnnotation(BuiltinAnnotation.Provides) != null
        }
    }
}