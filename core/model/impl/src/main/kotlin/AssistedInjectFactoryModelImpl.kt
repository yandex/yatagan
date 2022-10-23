package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.AssistedFactory
import com.yandex.daggerlite.AssistedInject
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel.Parameter
import com.yandex.daggerlite.core.model.HasNodeModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.TypeDeclarationKind
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class AssistedInjectFactoryModelImpl private constructor(
    private val impl: TypeDeclarationLangModel,
) : AssistedInjectFactoryModel {
    init {
        assert(canRepresent(impl))
    }

    override val factoryMethod: FunctionLangModel? by lazy {
        impl.functions.singleOrNull { it.isAbstract }
    }

    private val assistedInjectType: TypeLangModel? by lazy {
        factoryMethod?.returnType
    }

    override val assistedInjectConstructor by lazy {
        assistedInjectType?.declaration?.constructors?.find {
            it.isAnnotatedWith<AssistedInject>()
        }
    }

    override val assistedConstructorParameters by lazy {
        assistedInjectConstructor?.parameters?.map { parameter ->
            when (val assisted = parameter.assistedAnnotationIfPresent) {
                null -> Parameter.Injected(NodeDependency(type = parameter.type, forQualifier = parameter))
                else -> Parameter.Assisted(identifier = assisted.value, type = parameter.type)
            }
        }?.toList() ?: emptyList()
    }

    override val assistedFactoryParameters by lazy {
        factoryMethod?.parameters?.map { parameter ->
            Parameter.Assisted(
                identifier = when (val assisted = parameter.assistedAnnotationIfPresent) {
                    null -> ""
                    else -> assisted.value
                },
                type = parameter.type,
            )
        }?.toList() ?: emptyList()
    }

    override val type: TypeLangModel
        get() = impl.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = impl.asType(),
        qualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitAssistedInjectFactory(this)
    }

    override fun validate(validator: Validator) {
        validator.child(asNode())

        if (impl.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNotInterface())
        }

        if (impl.functions.count { it.isAbstract } != 1) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNoMethod())
            return  // All the following errors here will be induced, skip them.
        }

        if (assistedFactoryParameters.size > assistedFactoryParameters.toSet().size) {
            validator.reportError(Strings.Errors.assistedInjectFactoryDuplicateParameters())
        }

        val assistedInjectType = assistedInjectType
        val assistedInjectConstructor = assistedInjectConstructor
        if (assistedInjectType == null || assistedInjectConstructor == null) {
            validator.reportError(Strings.Errors.assistedInjectTypeNoConstructor(assistedInjectType))
            return  // All the following errors here will be induced, skip them.
        }

        if (!assistedInjectType.declaration.isEffectivelyPublic || !assistedInjectConstructor.isEffectivelyPublic) {
            validator.reportError(Strings.Errors.invalidAccessForAssistedInject())
        }


        val allAssistedParameters = assistedConstructorParameters.filterIsInstance<Parameter.Assisted>()
        val allConstructorAssistedParameters: Set<Parameter.Assisted> = allAssistedParameters.toSet()
        if (allAssistedParameters.size > allConstructorAssistedParameters.size) {
            validator.reportError(Strings.Errors.assistedInjectDuplicateParameters())
        }

        val allFactoryAssistedParameters: Set<Parameter.Assisted> = assistedFactoryParameters.toSet()
        if (allConstructorAssistedParameters != allFactoryAssistedParameters) {
            validator.reportError(Strings.Errors.assistedInjectMismatch()) {
                addNote(Strings.Notes.assistedInjectMismatchFromConstructor(params = allConstructorAssistedParameters))
                addNote(Strings.Notes.assistedInjectMismatchFromFactory(params = allFactoryAssistedParameters))
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "assisted-factory",
        representation = impl,
    )

    companion object Factory : ObjectCache<TypeDeclarationLangModel, AssistedInjectFactoryModelImpl>() {
        operator fun invoke(declaration: TypeDeclarationLangModel): AssistedInjectFactoryModelImpl {
            return createCached(declaration, ::AssistedInjectFactoryModelImpl)
        }

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.isAnnotatedWith<AssistedFactory>()
        }
    }
}