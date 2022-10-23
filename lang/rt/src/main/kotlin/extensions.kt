package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.ConstructorLangModel
import com.yandex.daggerlite.lang.FieldLangModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.TypeDeclarationLangModel

/**
 * Convenience extension for creating [MethodSignatureEquivalenceWrapper] for the given [Method].
 */
fun ReflectMethod.signatureEquivalence() = MethodSignatureEquivalenceWrapper(this)

// region RT types accessors for `platformModel` property

val FunctionLangModel.rt
    get() = platformModel as ReflectMethod

val FieldLangModel.rt
    get() = platformModel as ReflectField

val TypeDeclarationLangModel.rt
    get() = platformModel as Class<*>

val ConstructorLangModel.rt
    get() = platformModel as ReflectConstructor

val AnnotationLangModel.Value.rawValue: Any
    get() = checkNotNull(platformModel)

// endregion