package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.AssistedFactory
import com.yandex.daggerlite.AssistedInject
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.AssistedInjectFactoryModel.Parameter
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal class AssistedInjectFactoryModelImpl private constructor(
    private val impl: TypeDeclarationLangModel,
) : AssistedInjectFactoryModel {
    init {
        assert(canRepresent(impl))
    }

    override val factoryMethod: FunctionLangModel? by lazy {
        impl.functions.singleOrNull { it.isAbstract }
    }

    private val assistedInjectType: TypeLangModel by lazy {
        factoryMethod?.returnType ?: LangModelFactory.errorType
    }

    override val assistedInjectConstructor by lazy {
        assistedInjectType.declaration.constructors.find {
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

        if (!impl.isInterface) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNotInterface())
        }

        if (impl.functions.count { it.isAbstract } != 1) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNoMethod())
            return  // All the following errors here will be induced, skip them.
        }

        if (assistedFactoryParameters.size > assistedFactoryParameters.toSet().size) {
            validator.reportError(Strings.Errors.assistedInjectFactoryDuplicateParameters())
        }

        val assistedInjectConstructor = assistedInjectConstructor
        if (assistedInjectConstructor == null) {
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
                addNote("From constructor: $allConstructorAssistedParameters")
                addNote("From factory: $allFactoryAssistedParameters")
            }
        }
    }

    override fun toString(): String {
        return "[assisted factory] $impl"
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, AssistedInjectFactoryModelImpl>() {
        operator fun invoke(declaration: TypeDeclarationLangModel): AssistedInjectFactoryModelImpl {
            return createCached(declaration, ::AssistedInjectFactoryModelImpl)
        }

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.isAnnotatedWith<AssistedFactory>()
        }
    }
}