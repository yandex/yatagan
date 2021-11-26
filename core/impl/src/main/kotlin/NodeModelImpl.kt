package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BootstrapInterface
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.InjectConstructorBindingModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

internal class NodeModelImpl private constructor(
    override val type: TypeLangModel,
    override val qualifier: AnnotationLangModel?,
) : NodeModel {

    init {
        require(when (type.declaration.qualifiedName) {
            Names.Lazy, Names.Provider, Names.Optional -> false
            else -> true
        }) {
            "$type constitutes a framework type which is prohibited"
        }
    }

    override val implicitBinding: InjectConstructorBindingModel?
        get() = if (qualifier == null) {
            type.declaration.constructors.find { it.isAnnotatedWith<Inject>() }?.let {
                InjectConstructorImpl(constructor = it)
            }
        } else null

    private inner class InjectConstructorImpl(
        override val constructor: ConstructorLangModel,
    ) : InjectConstructorBindingModel, ConditionalHoldingModelImpl(constructor.constructee.conditionals) {
        init {
            require(constructor.isAnnotatedWith<Inject>())
        }

        override val inputs: Sequence<NodeDependency> = constructor.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.memoize()

        override val target: NodeModel
            get() = this@NodeModelImpl

        override val scope: AnnotationLangModel? by lazy {
            constructor.constructee.annotations.find(AnnotationLangModel::isScope)
        }
    }

    override val bootstrapInterfaces: Collection<BootstrapInterfaceModel> by lazy(NONE) {
        type.declaration.implementedInterfaces
            .filter { it.declaration.isAnnotatedWith<BootstrapInterface>() }
            .map { BootstrapInterfaceModelImpl(it) }
            .toList()
    }

    override fun toString() = buildString {
        qualifier?.let {
            append('@')
            append(qualifier)
            append(' ')
        }
        append(type)
    }

    companion object Factory : BiObjectCache<TypeLangModel, AnnotationLangModel?, NodeModelImpl>() {
        operator fun invoke(
            type: TypeLangModel,
            forQualifier: AnnotatedLangModel?,
        ) = this(type, forQualifier?.annotations?.find(AnnotationLangModel::isQualifier))

        operator fun invoke(
            type: TypeLangModel,
            qualifier: AnnotationLangModel?,
        ) = createCached(type, qualifier) {
            NodeModelImpl(
                type = type,
                qualifier = qualifier,
            )
        }
    }
}