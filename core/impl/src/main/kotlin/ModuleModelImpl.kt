package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import kotlin.LazyThreadSafetyMode.NONE

internal class ModuleModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ModuleModel {
    init {
        require(canRepresent(declaration))
    }

    private val impl = declaration.moduleAnnotationIfPresent!!

    override val type: TypeLangModel
        get() = declaration.asType()

    override val includes: Collection<ModuleModel> by lazy(NONE) {
        impl.includes.map(TypeLangModel::declaration).map(Factory::invoke).toSet()
    }

    override val subcomponents: Collection<ComponentModel> by lazy(NONE) {
        impl.subcomponents.map(TypeLangModel::declaration).map { ComponentModelImpl(it) }.toSet()
    }

    override val bootstrap: Collection<NodeModel> by lazy(NONE) {
        impl.bootstrap.map { NodeModelImpl(type = it, forQualifier = null) }.toList()
    }

    override val requiresInstance: Boolean
        get() = mayRequireInstance() && declaration.allPublicFunctions.any { method ->
            (method.isAnnotatedWith<Binds>() || method.providesAnnotationIfPresent != null) &&
                    (!method.isStatic && !method.isFromCompanionObject)
        }

    override fun bindings(forGraph: BindingGraph): Sequence<BaseBinding> = sequence {
        val mayRequireInstance = mayRequireInstance()

        for (method in declaration.allPublicFunctions) {
            fun target(): NodeModel = NodeModelImpl(
                type = method.returnType,
                forQualifier = method,
            )
            val providesAnnotation = method.providesAnnotationIfPresent
            when {
                providesAnnotation != null -> {
                    // @Provides
                    val scope = if (providesAnnotation.conditionals.any()) {
                        matchConditionScopeFromConditionals(
                            forVariant = forGraph.variant,
                            conditionals = providesAnnotation.conditionals,
                        )
                    } else ConditionScope.Unscoped
                    if (scope == null) {
                        // Ruled out by variant constraints
                        yield(EmptyBindingImpl(
                            owner = forGraph,
                            target = target(),
                        ))
                    } else {
                        yield(ProvisionBindingImpl(
                            owner = forGraph,
                            target = target(),
                            provider = method,
                            scope = method.annotations.find(AnnotationLangModel::isScope),
                            requiredModuleInstance = this@ModuleModelImpl.takeIf {
                                mayRequireInstance && !method.isStatic && !method.isFromCompanionObject
                            },
                            params = method.parameters.map { param ->
                                NodeDependency(type = param.type, forQualifier = param)
                            }.toList(),
                            conditionScope = scope,
                        ))
                    }
                }
                method.isAnnotatedWith<Binds>() -> {
                    // @Binds
                    yield(when (method.parameters.count()) {
                        0 -> throw IllegalStateException("@Binds with no arguments")
                        1 -> AliasBindingImpl(
                            owner = forGraph,
                            target = target(),
                            source = NodeModelImpl(
                                type = method.parameters.single().type,
                                forQualifier = method.parameters.single(),
                            ),
                        )
                        else -> AlternativesBindingImpl(
                            owner = forGraph,
                            target = target(),
                            alternatives = method.parameters.map { parameter ->
                                NodeModelImpl(type = parameter.type, forQualifier = parameter)
                            }.toList(),
                            scope = method.annotations.find(AnnotationLangModel::isScope),
                        )
                    })
                }
            }
        }
    }

    override fun toString() = "Module[$declaration]"

    private fun mayRequireInstance() = !declaration.isAbstract && !declaration.isKotlinObject

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}