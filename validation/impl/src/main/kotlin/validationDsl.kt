package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.ValidationMessage.Kind
import com.yandex.daggerlite.validation.Validator

/**
 * An API for building [messages][ValidationMessage].
 */
interface ValidationMessageBuilder {
    var contents: String
    fun addNote(note: String)
}

inline fun buildMessage(kind: Kind, block: ValidationMessageBuilder.() -> Unit): ValidationMessage {
    return ValidationMessageBuilderImpl(kind)
        .apply(block)
        .build()
}

@Suppress("DEPRECATION")
inline fun Validator.reportError(message: String, block: ValidationMessageBuilder.() -> Unit = {}) {
    report(buildMessage(Kind.Error) {
        contents = message
        block()
    })
}

@Suppress("DEPRECATION")
inline fun Validator.reportWarning(message: String, block: ValidationMessageBuilder.() -> Unit = {}) {
    report(buildMessage(Kind.Warning) {
        contents = message
        block()
    })
}