package com.yandex.daggerlite.core.lang

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun <reified A : Annotation> AnnotatedLangModel.isAnnotatedWith() = isAnnotatedWith(A::class.java)

inline fun <reified A : Annotation> AnnotationLangModel.hasType() = annotationClass.isClass(A::class.java)

val TypeDeclarationLangModel.isKotlinObject get() = kotlinObjectKind != null

val TypeDeclarationLangModel.functionsWithCompanion: Sequence<FunctionLangModel>
    get() = when (val companion = defaultCompanionObjectDeclaration) {
        null -> functions
        else -> functions + companion.functions
    }

@OptIn(InternalLangApi::class)
inline fun LangModelFactory.Companion.use(factory: LangModelFactory, block: () -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    check(delegate == null)
    delegate = factory
    try {
        block()
    } finally {
        delegate = null
    }
}

abstract class AnnotationValueVisitorAdapter<R> : AnnotationLangModel.Value.Visitor<R> {
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
    override fun visitType(value: TypeLangModel) = visitDefault()
    override fun visitAnnotation(value: AnnotationLangModel) = visitDefault()
    override fun visitEnumConstant(enum: TypeLangModel, constant: String) = visitDefault()
    override fun visitArray(value: List<AnnotationLangModel.Value>) = visitDefault()
    override fun visitUnresolved() = visitDefault()
}