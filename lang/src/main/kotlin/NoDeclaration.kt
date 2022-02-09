package com.yandex.daggerlite.core.lang

/**
 * A [TypeDeclarationLangModel] implementation, that is convenient to return when no declaration makes sense at all,
 * e.g. for primitive types, `void` type, array type, etc.
 */
class NoDeclaration (
    private val type: TypeLangModel,
) : TypeDeclarationLangModel {
    override val isAbstract get() = false
    override val isInterface get() = false

    override val annotations get() = emptySequence<Nothing>()
    override val implementedInterfaces get() = emptySequence<Nothing>()
    override val constructors get() = emptySequence<Nothing>()
    override val allPublicFunctions get() = emptySequence<Nothing>()
    override val allPublicFields get() = emptySequence<Nothing>()
    override val nestedClasses get() = emptySequence<Nothing>()
    override val conditions get() = emptySequence<Nothing>()
    override val conditionals get() = emptySequence<Nothing>()

    override val enclosingType: Nothing? get() = null
    override val kotlinObjectKind: Nothing? get() = null
    override val componentAnnotationIfPresent: Nothing? get() = null
    override val moduleAnnotationIfPresent: Nothing? get() = null
    override val componentFlavorIfPresent: Nothing? get() = null
    override val platformModel: Nothing? get() = null

    override val qualifiedName: String
        get() = type.toString()

    override fun asType(): TypeLangModel = type


    override fun hashCode(): Int = type.hashCode()
    override fun equals(other: Any?): Boolean {
        return this === other || (other is NoDeclaration && type == other.type)
    }
}