@file:Suppress("NOTHING_TO_INLINE")

package com.yandex.daggerlite.lang

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

val TypeDeclarationLangModel.isKotlinObject get() = when(kind) {
    TypeDeclarationKind.KotlinObject, TypeDeclarationKind.KotlinCompanion -> true
    else -> false
}

val TypeDeclarationLangModel.functionsWithCompanion: Sequence<Method>
    get() = when (val companion = defaultCompanionObjectDeclaration) {
        null -> methods
        else -> methods + companion.methods
    }

@OptIn(InternalLangApi::class)
inline fun LangModelFactory.Companion.use(factory: LangModelFactory, block: () -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    check(delegate == null)
    delegate = factory
    try {
        block()
    } finally {
        check(delegate == factory)
        delegate = null
    }
}

abstract class AnnotationValueVisitorAdapter<R> : Annotation.Value.Visitor<R> {
    abstract fun visitDefault(): R
    override fun visitBoolean(value: Boolean) = visitDefault()
    override fun visitByte(value: Byte) = visitDefault()
    override fun visitShort(value: Short) = visitDefault()
    override fun visitInt(value: Int) = visitDefault()
    override fun visitLong(value: Long) = visitDefault()
    override fun visitChar(value: Char) = visitDefault()
    override fun visitFloat(value: Float) = visitDefault()
    override fun visitDouble(value: Double) = visitDefault()
    override fun visitString(value: String) = visitDefault()
    override fun visitType(value: Type) = visitDefault()
    override fun visitAnnotation(value: Annotation) = visitDefault()
    override fun visitEnumConstant(enum: Type, constant: String) = visitDefault()
    override fun visitArray(value: List<Annotation.Value>) = visitDefault()
    override fun visitUnresolved() = visitDefault()
}

inline fun LangModelFactory.getListType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.List,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

inline fun LangModelFactory.getSetType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Set,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

inline fun LangModelFactory.getCollectionType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Collection,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

inline fun LangModelFactory.getProviderType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Provider,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}