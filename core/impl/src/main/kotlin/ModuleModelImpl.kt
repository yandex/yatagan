package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
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

    private class BindsImpl(
        val impl: FunctionLangModel,
        override val originModule: ModuleModel,
    ) : BindsBindingModel {

        init {
            require(canRepresent(impl))
        }

        override val scope: AnnotationLangModel? by lazy(NONE) {
            impl.annotations.find(AnnotationLangModel::isScope)
        }

        override val sources = impl.parameters.map { parameter ->
            NodeModelImpl(type = parameter.type, forQualifier = parameter)
        }

        override val target: NodeModel by lazy(NONE) {
            NodeModelImpl(type = impl.returnType, forQualifier = impl)
        }

        companion object {
            fun canRepresent(method: FunctionLangModel): Boolean {
                return method.isAnnotatedWith<Binds>()
            }
        }
    }

    private class ProvidesImpl(
        private val impl: FunctionLangModel,
        override val originModule: ModuleModelImpl,
    ) : ProvidesBindingModel, ConditionalHoldingModelImpl(impl.providesAnnotationIfPresent!!.conditionals) {

        override val scope: AnnotationLangModel? by lazy(NONE) {
            impl.annotations.find(AnnotationLangModel::isScope)
        }

        override val target: NodeModel by lazy(NONE) {
            NodeModelImpl(type = impl.returnType, forQualifier = impl)
        }

        override val inputs: Sequence<NodeDependency> = impl.parameters.map { param ->
            NodeDependency(type = param.type, forQualifier = param)
        }.memoize()

        override val provision: FunctionLangModel
            get() = impl

        override val requiresModuleInstance: Boolean
            get() = originModule.mayRequireInstance && !impl.isStatic && !impl.owner.isKotlinObject

        companion object {
            fun canRepresent(method: FunctionLangModel): Boolean {
                return method.providesAnnotationIfPresent != null
            }
        }
    }

    override fun toString() = "Module[$declaration]"

    private val mayRequireInstance by lazy(NONE) {
        !declaration.isAbstract && !declaration.isKotlinObject
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}

