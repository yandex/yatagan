package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentVariantDimension
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.DimensionElementModel
import com.yandex.daggerlite.core.DimensionModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.VariantModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentModelImpl(
    private val declaration: TypeDeclarationLangModel,
) : ComponentModel() {
    init {
        require(canRepresent(declaration))
    }

    private val impl = declaration.componentAnnotationIfPresent!!

    override val type: TypeLangModel
        get() = declaration.asType()

    override val scope = declaration.annotations.find(AnnotationLangModel::isScope)

    override val modules: Set<ModuleModel> by lazy {
        impl.modules.map(TypeLangModel::declaration).map(::ModuleModelImpl).toSet()
    }
    override val dependencies: Set<ComponentModel> by lazy {
        impl.dependencies.map(TypeLangModel::declaration).map(::ComponentModelImpl).toSet()
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

    override val factory: ComponentFactoryModel? by lazy {
        val factoryDeclaration: TypeDeclarationLangModel = declaration.nestedInterfaces
            .find { it.isAnnotatedWith<Component.Factory>() } ?: return@lazy null

        val factoryMethod = checkNotNull(factoryDeclaration.allPublicFunctions.find { it.name == "create" }) {
            "no 'create' method in factory"
        }

        ComponentFactoryModelImpl(
            createdComponent = this,
            factoryDeclaration = factoryDeclaration,
            factoryMethod = factoryMethod,
        )
    }

    override val isRoot: Boolean = impl.isRoot

    override val variant: VariantModel by lazy(NONE) {
        object : VariantModel {
            override val parts: Map<DimensionModel, DimensionElementModel> =
                impl.variant
                    .map(::DimensionElementImpl)
                    .associateBy(DimensionElementImpl::dimension)
        }
    }

    override fun conditionScope(forVariant: VariantModel): ConditionScope? {
        val conditionals = declaration.conditionals
        return if (conditionals.any()) {
            matchConditionScopeFromConditionals(
                forVariant = forVariant,
                conditionals = conditionals,
            ) ?: return null  // component is excluded from this component by variant filter
        } else ConditionScope.Unscoped
    }

    companion object {
        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.componentAnnotationIfPresent != null
        }
    }
}

internal class DimensionImpl(
    override val type: TypeLangModel,
) : DimensionModel() {
    init {
        require(type.declaration.isAnnotatedWith<ComponentVariantDimension>())
    }
}

internal class DimensionElementImpl(
    override val type: TypeLangModel,
) : DimensionElementModel() {
    override val dimension: DimensionModel =
        DimensionImpl(checkNotNull(type.declaration.componentFlavorIfPresent).dimension)
}
