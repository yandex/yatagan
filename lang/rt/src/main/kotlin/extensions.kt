package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.Constructor
import com.yandex.daggerlite.lang.Field
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.lang.TypeDeclarationLangModel

/**
 * Convenience extension for creating [MethodSignatureEquivalenceWrapper] for the given [Method].
 */
fun ReflectMethod.signatureEquivalence() = MethodSignatureEquivalenceWrapper(this)

// region RT types accessors for `platformModel` property

val Method.rt
    get() = platformModel as ReflectMethod

val Field.rt
    get() = platformModel as ReflectField

val TypeDeclarationLangModel.rt
    get() = platformModel as Class<*>

val Constructor.rt
    get() = platformModel as ReflectConstructor

val AnnotationLangModel.Value.rawValue: Any
    get() = checkNotNull(platformModel)

// endregion