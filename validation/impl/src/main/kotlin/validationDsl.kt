package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.ValidationMessage.Kind

/**
 * An API for building [messages][ValidationMessage].
 */
interface ValidationMessageBuilder {
    var contents: String
    fun add(message: ValidationMessage)
}

inline fun buildMessage(kind: Kind, block: ValidationMessageBuilder.() -> Unit): ValidationMessage {
    return ValidationMessageBuilderImpl(kind)
        .apply(block)
        .build()
}

inline fun buildError(block: ValidationMessageBuilder.() -> Unit): ValidationMessage {
    return buildMessage(Kind.Error, block)
}

inline fun buildWarning(block: ValidationMessageBuilder.() -> Unit): ValidationMessage {
    return buildMessage(Kind.Warning, block)
}

inline fun buildNote(block: ValidationMessageBuilder.() -> Unit): ValidationMessage {
    return buildMessage(Kind.Warning, block)
}

inline fun ValidationMessageBuilder.addNote(block: ValidationMessageBuilder.() -> Unit) {
    add(buildMessage(Kind.Note, block))
}