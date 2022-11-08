package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.TypeDeclaration

/**
 * Convenience extension for creating [MethodSignatureEquivalenceWrapper] for the given [Method].
 */
fun ReflectMethod.signatureEquivalence() = MethodSignatureEquivalenceWrapper(this)

// region RT types accessors for `platformModel` property

val Method.rt
    get() = platformModel as ReflectMethod

val Field.rt
    get() = platformModel as ReflectField

val TypeDeclaration.rt
    get() = platformModel as Class<*>

val Constructor.rt
    get() = platformModel as ReflectConstructor

val Annotation.Value.rawValue: Any
    get() = checkNotNull(platformModel)

// endregion