package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.DeclareList
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.core.lang.isKotlinObject
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
import kotlin.LazyThreadSafetyMode.NONE

internal class ModuleModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ModuleModel {
    private val impl = declaration.moduleAnnotationIfPresent

    override val type: TypeLangModel
        get() = declaration.asType()

    override val includes: Collection<ModuleModel> by lazy(NONE) {
        impl?.includes?.map(TypeLangModel::declaration)?.map(Factory::invoke)?.toSet() ?: emptySet()
    }

    override val subcomponents: Collection<ComponentModel> by lazy(NONE) {
        impl?.subcomponents?.map(TypeLangModel::declaration)?.map { ComponentModelImpl(it) }?.toSet() ?: emptySet()
    }

    override val listDeclarations: Sequence<ListDeclarationModel> =
        declaration.allPublicFunctions
            .filter { it.isAnnotatedWith<DeclareList>() }
            .map { method ->
                ListDeclarationImpl(
                    function = method,
                )
            }.memoize()

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

    override fun toString() = declaration.toString()

    internal val mayRequireInstance by lazy(NONE) {
        !declaration.isAbstract && !declaration.isKotlinObject
    }

    override fun validate(validator: Validator) {
        if (impl == null) {
            validator.reportError(Errors.`declaration is not annotated with @Module`())
        }
        for (binding in bindings) {
            validator.child(binding)
        }
        for (module in includes) {
            validator.child(module)
        }
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}

