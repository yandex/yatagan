package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import javax.inject.Inject

internal class NodeModelImpl private constructor(
    override val type: TypeLangModel,
    override val qualifier: AnnotationLangModel?,
) : NodeModel {

    override fun implicitBinding(forGraph: BindingGraph): Binding? {
        if (qualifier != null)
            return null

        val conditionals = type.declaration.conditionals
        val conditionScope = if (conditionals.any()) {
            matchConditionScopeFromConditionals(
                forVariant = forGraph.model.variant,
                conditionals = conditionals,
            ) ?: return EmptyBindingImpl(
                owner = forGraph,
                target = this,
            )
        } else ConditionScope.Unscoped

        return type.declaration.constructors.find { it.isAnnotatedWith<Inject>() }?.let { injectConstructor ->
            ProvisionBindingImpl(
                owner = forGraph,
                target = this,
                requiredModuleInstance = null,
                scope = type.declaration.annotations.find(AnnotationLangModel::isScope),
                provider = injectConstructor,
                params = injectConstructor.parameters.map { param ->
                    nodeModelDependency(type = param.type, forQualifier = param)
                }.toList(),
                conditionScope = conditionScope,
            )
        }
    }

    override fun toString() = buildString {
        qualifier?.let {
            append(qualifier)
            append(' ')
        }
        append(type)
    }

    companion object Factory : BiObjectCache<TypeLangModel, AnnotationLangModel?, NodeModelImpl>() {
        operator fun invoke(
            type: TypeLangModel,
            forQualifier: AnnotatedLangModel?,
        ) = createCached(type, forQualifier?.annotations?.find(AnnotationLangModel::isQualifier)) { _, qualifier ->
            NodeModelImpl(
                type = type,
                qualifier = qualifier,
            )
        }
    }
}