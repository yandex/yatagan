package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isKotlinObject
import kotlin.LazyThreadSafetyMode.NONE

internal class ModuleModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ModuleModel {
    init {
        require(canRepresent(declaration)) {
            "$declaration is not a Dagger Lite Module"
        }
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

    override val declaredBootstrapInterfaces: Collection<BootstrapInterfaceModel> by lazy(NONE) {
        impl.bootstrap.filter { BootstrapInterfaceModelImpl.canRepresent(it) }
            .map { BootstrapInterfaceModelImpl(it) }
            .toList()
    }

    override val bootstrap: Collection<NodeModel> by lazy(NONE) {
        impl.bootstrap.filter { !BootstrapInterfaceModelImpl.canRepresent(it) }
            .map { NodeModelImpl(it, forQualifier = null) }
            .onEach {
                // TODO: turn this into validation
                check(it.bootstrapInterfaces.any()) {
                    "$it is declared in a bootstrap list though implements no bootstrap interfaces"
                }
            }.toList()
    }

    override val requiresInstance: Boolean by lazy(NONE) {
        mayRequireInstance && bindings.any { it is ProvidesBindingModel && it.requiresModuleInstance }
    }

    override val isTriviallyConstructable: Boolean
        get() = mayRequireInstance && declaration.constructors.any { it.parameters.none() }

    override val bindings: Sequence<ModuleHostedBindingModel> = declaration.allPublicFunctions.mapNotNull { method ->
        when {
            BindsImpl.canRepresent(method) -> BindsImpl(
                impl = method,
                originModule = this@ModuleModelImpl,
            )
            ProvidesImpl.canRepresent(method) -> ProvidesImpl(
                impl = method,
                originModule = this@ModuleModelImpl,
            )
            else -> null
        }
    }.memoize()

    override fun toString() = "Module[$declaration]"

    internal val mayRequireInstance by lazy(NONE) {
        !declaration.isAbstract && !declaration.isKotlinObject
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}

