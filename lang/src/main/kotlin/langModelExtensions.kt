package com.yandex.daggerlite.core.lang

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun <reified A : Annotation> AnnotatedLangModel.isAnnotatedWith() = isAnnotatedWith(A::class.java)

inline fun <reified A : Annotation> AnnotationLangModel.hasType() = hasType(A::class.java)

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