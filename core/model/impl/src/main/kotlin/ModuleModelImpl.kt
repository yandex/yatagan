package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.Multibinds
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.model.BindsBindingModel
import com.yandex.daggerlite.core.model.ComponentModel
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel
import com.yandex.daggerlite.core.model.ModuleModel
import com.yandex.daggerlite.core.model.MultiBindingDeclarationModel
import com.yandex.daggerlite.core.model.ProvidesBindingModel
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.functionsWithCompanion
import com.yandex.daggerlite.lang.isAnnotatedWith
import com.yandex.daggerlite.lang.isKotlinObject
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class ModuleModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ModuleModel {
    private val impl = declaration.moduleAnnotationIfPresent

    override val type: TypeLangModel
        get() = declaration.asType()

    override val includes: Collection<ModuleModel> by lazy {
        impl?.includes?.map(TypeLangModel::declaration)?.map(Factory::invoke)?.toSet() ?: emptySet()
    }

    override val subcomponents: Collection<ComponentModel> by lazy {
        impl?.subcomponents?.map(TypeLangModel::declaration)?.map { ComponentModelImpl(it) }?.toSet() ?: emptySet()
    }

    override val multiBindingDeclarations: Sequence<MultiBindingDeclarationModel> =
        declaration.functions
            .filter { it.isAnnotatedWith<Multibinds>() }
            .map { method ->
                when {
                    CollectionDeclarationImpl.canRepresent(method) -> CollectionDeclarationImpl(method)
                    MapDeclarationImpl.canRepresent(method) -> MapDeclarationImpl(method)
                    else -> InvalidDeclarationImpl(invalidMethod = method)
                }
            }.memoize()

    override val requiresInstance: Boolean by lazy {
        mayRequireInstance && bindings.any { it.accept(AsProvides)?.requiresModuleInstance == true }
    }

    override val isTriviallyConstructable: Boolean
        get() = mayRequireInstance && declaration.constructors.any { it.isEffectivelyPublic && it.parameters.none() }

    override val bindings: Sequence<ModuleHostedBindingModel> = declaration.functionsWithCompanion.mapNotNull { method ->
        when {
            BindsImpl.canRepresent(method) -> BindsImpl(
                function = method,
                originModule = this@ModuleModelImpl,
            )
            ProvidesImpl.canRepresent(method) -> ProvidesImpl(
                function = method,
                originModule = this@ModuleModelImpl,
            )
            else -> null
        }
    }.memoize()

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "module",
        representation = declaration,
    )

    internal val mayRequireInstance by lazy(PUBLICATION) {
        !declaration.isAbstract && !declaration.isKotlinObject
    }

    override fun validate(validator: Validator) {
        if (impl == null) {
            validator.reportError(Strings.Errors.nonModule())
        }
        for (binding in bindings) {
            validator.child(binding)
        }
        for (module in includes) {
            validator.child(module)
        }
        if (!declaration.isEffectivelyPublic && bindings.any { it.accept(AsProvides) != null }) {
            validator.reportError(Strings.Errors.invalidAccessForModuleClass())
        }
        for (declaration in multiBindingDeclarations) {
            validator.child(declaration)
        }
    }

    private object AsProvides : ModuleHostedBindingModel.Visitor<ProvidesBindingModel?> {
        override fun visitBinds(model: BindsBindingModel) = null
        override fun visitProvides(model: ProvidesBindingModel) = model
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}
