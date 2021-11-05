package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ComponentModel.EntryPoint
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ComponentModel {
    init {
        require(canRepresent(declaration))
    }

    private val impl = declaration.componentAnnotationIfPresent!!

    override val type: TypeLangModel
        get() = declaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = declaration.asType(),
        forQualifier = null,
    )

    override val scope = declaration.annotations.find(AnnotationLangModel::isScope)

    override val modules: Set<ModuleModel> by lazy {
        impl.modules.map(TypeLangModel::declaration).map { ModuleModelImpl(it) }.toSet()
    }
    override val dependencies: Set<ComponentModel> by lazy {
        impl.dependencies.map(TypeLangModel::declaration).map(Factory::invoke).toSet()
    }
    override val entryPoints: Set<EntryPoint> by lazy {
        buildSet {
            for (function in declaration.allPublicFunctions.filter { it.isAbstract }) {
                this += EntryPoint(
                    dependency = nodeModelDependency(
                        type = function.returnType,
                        forQualifier = function,
                    ),
                    getter = function,
                )
            }
        }
    }

    override val factory: ComponentFactoryModel? by lazy(NONE) {
        declaration.nestedInterfaces
            .find { it.isAnnotatedWith<Component.Factory>() }?.let { ComponentFactoryModelImpl(it) }
    }

    override val isRoot: Boolean = impl.isRoot

    override val variant: Variant by lazy(NONE) {
        VariantImpl(impl.variant)
    }

    override fun conditionScope(forVariant: Variant): ConditionScope? {
        val conditionals = declaration.conditionals
        return if (conditionals.any()) {
            matchConditionScopeFromConditionals(
                forVariant = forVariant,
                conditionals = conditionals,
            ) ?: return null  // component is excluded from this component by variant filter
        } else ConditionScope.Unscoped
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ComponentModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ComponentModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.componentAnnotationIfPresent != null
        }
    }
}
