package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith

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

    companion object {
        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.componentAnnotationIfPresent != null
        }
    }
}