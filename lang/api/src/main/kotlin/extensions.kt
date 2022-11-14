@file:Suppress("NOTHING_TO_INLINE")

package com.yandex.yatagan.lang

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public val TypeDeclaration.isKotlinObject: Boolean
    get() = when (kind) {
        TypeDeclarationKind.KotlinObject, TypeDeclarationKind.KotlinCompanion -> true
        else -> false
    }

public val TypeDeclaration.functionsWithCompanion: Sequence<Method>
    get() = when (val companion = defaultCompanionObjectDeclaration) {
        null -> methods
        else -> methods + companion.methods
    }

@OptIn(InternalLangApi::class)
public inline fun LangModelFactory.Companion.use(factory: LangModelFactory, block: () -> Unit) {
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

public abstract class AnnotationValueVisitorAdapter<R> : Annotation.Value.Visitor<R> {
    public abstract fun visitDefault(): R
    override fun visitBoolean(value: Boolean): R = visitDefault()
    override fun visitByte(value: Byte): R = visitDefault()
    override fun visitShort(value: Short): R = visitDefault()
    override fun visitInt(value: Int): R = visitDefault()
    override fun visitLong(value: Long): R = visitDefault()
    override fun visitChar(value: Char): R = visitDefault()
    override fun visitFloat(value: Float): R = visitDefault()
    override fun visitDouble(value: Double): R = visitDefault()
    override fun visitString(value: String): R = visitDefault()
    override fun visitType(value: Type): R = visitDefault()
    override fun visitAnnotation(value: Annotation): R = visitDefault()
    override fun visitEnumConstant(enum: Type, constant: String): R = visitDefault()
    override fun visitArray(value: List<Annotation.Value>): R = visitDefault()
    override fun visitUnresolved(): R = visitDefault()
}

public inline fun LangModelFactory.getListType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.List,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

public inline fun LangModelFactory.getSetType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Set,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

public inline fun LangModelFactory.getCollectionType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Collection,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}

public inline fun LangModelFactory.getProviderType(parameter: Type, isCovariant: Boolean = false): Type {
    return getParameterizedType(
        type = LangModelFactory.ParameterizedType.Provider,
        parameter = parameter,
        isCovariant = isCovariant,
    )
}