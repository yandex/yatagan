package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Convenience extension for creating [MethodSignatureEquivalenceWrapper] for the given [Method].
 */
fun Method.signatureEquivalence() = MethodSignatureEquivalenceWrapper(this)

// region RT types accessors for `platformModel` property

val FunctionLangModel.rt
    get() = platformModel as Method

val FieldLangModel.rt
    get() = platformModel as Field

val TypeDeclarationLangModel.rt
    get() = platformModel as Class<*>

val ConstructorLangModel.rt
    get() = platformModel as Constructor<*>

// endregion