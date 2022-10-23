package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.lang.TypeDeclarationKind
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.TypeLangModel

/**
 * A [TypeDeclarationLangModel] implementation, that is convenient to return when no declaration makes sense at all,
 * e.g. for primitive types, `void` type, array type, etc.
 */
class NoDeclaration(
    private val type: TypeLangModel,
) : TypeDeclarationLangModelBase() {
    override val isAbstract get() = false
    override val isEffectivelyPublic get() = false

    override val annotations get() = emptySequence<Nothing>()
    override val interfaces get() = emptySequence<Nothing>()
    override val constructors get() = emptySequence<Nothing>()
    override val functions get() = emptySequence<Nothing>()
    override val fields get() = emptySequence<Nothing>()
    override val nestedClasses get() = emptySequence<Nothing>()
    override val conditions get() = emptySequence<Nothing>()
    override val conditionals get() = emptySequence<Nothing>()

    override val superType: Nothing? get() = null
    override val defaultCompanionObjectDeclaration: Nothing? get() = null
    override val enclosingType: Nothing? get() = null
    override val componentAnnotationIfPresent: Nothing? get() = null
    override val moduleAnnotationIfPresent: Nothing? get() = null
    override val componentFlavorIfPresent: Nothing? get() = null
    override val platformModel: Nothing? get() = null

    override val kind: TypeDeclarationKind
        get() = TypeDeclarationKind.None

    override val qualifiedName: String
        get() = type.toString()

    override fun asType(): TypeLangModel = type

    override fun hashCode(): Int = type.hashCode()
    override fun equals(other: Any?): Boolean {
        return this === other || (other is NoDeclaration && type == other.type)
    }
}