package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.base.ObjectCache

/**
 * A [CtTypeDeclarationLangModel] implementation, that is convenient to return when no declaration makes sense at all,
 * e.g. for primitive types, `void` type, array type, etc.
 */
class CtNoDeclaration private constructor(
    private val type: CtTypeLangModel,
) : CtTypeDeclarationLangModel() {
    override val isAbstract get() = false

    override val annotations get() = emptySequence<Nothing>()
    override val implementedInterfaces get() = emptySequence<Nothing>()
    override val constructors get() = emptySequence<Nothing>()
    override val allPublicFunctions get() = emptySequence<Nothing>()
    override val allPublicFields get() = emptySequence<Nothing>()
    override val nestedInterfaces get() = emptySequence<Nothing>()
    override val conditions get() = emptySequence<Nothing>()
    override val conditionals get() = emptySequence<Nothing>()

    override val kotlinObjectKind: Nothing? get() = null
    override val componentAnnotationIfPresent: Nothing? get() = null
    override val moduleAnnotationIfPresent: Nothing? get() = null
    override val componentFlavorIfPresent: Nothing? get() = null

    override val qualifiedName: String
        get() = type.nameModel.toString()

    override fun asType(): CtTypeLangModel {
        return type
    }

    companion object Factory : ObjectCache<CtTypeLangModel, CtNoDeclaration>() {
        operator fun invoke(type: CtTypeLangModel) = createCached(type, ::CtNoDeclaration)
    }
}