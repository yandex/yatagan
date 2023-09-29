/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.validation.format

import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.Validator

/**
 * An API for building [messages][ValidationMessage].
 */
interface ValidationMessageBuilder {
    fun addNote(note: NoteMessage)
}

inline fun buildError(
    message: ErrorMessage,
    block: ValidationMessageBuilder.() -> Unit = {}
): ValidationMessage {
    return ValidationMessageBuilderImpl(
        kind = ValidationMessage.Kind.Error,
        contents = message.text,
    ).apply(block).build()
}

inline fun buildWarning(
    message: WarningMessage,
    block: ValidationMessageBuilder.() -> Unit = {}
): ValidationMessage {
    return ValidationMessageBuilderImpl(
        kind = ValidationMessage.Kind.Warning,
        contents = message.text,
    ).apply(block).build()
}

inline fun buildMandatoryWarning(
    message: WarningMessage,
    block: ValidationMessageBuilder.() -> Unit = {}
): ValidationMessage {
    return ValidationMessageBuilderImpl(
        kind = ValidationMessage.Kind.MandatoryWarning,
        contents = message.text,
    ).apply(block).build()
}

inline fun Validator.reportError(message: ErrorMessage, block: ValidationMessageBuilder.() -> Unit = {}) {
    report(buildError(message = message, block = block))
}

inline fun Validator.reportWarning(message: WarningMessage, block: ValidationMessageBuilder.() -> Unit = {}) {
    report(buildWarning(message = message, block = block))
}

inline fun Validator.reportMandatoryWarning(message: WarningMessage, block: ValidationMessageBuilder.() -> Unit = {}) {
    report(buildMandatoryWarning(message = message, block = block))
}

fun ErrorMessage.demoteToWarning(): WarningMessage = WarningMessage(text)

@PublishedApi
internal class ValidationMessageBuilderImpl(
    private val kind: ValidationMessage.Kind,
    private val contents: CharSequence
) : ValidationMessageBuilder {
    private val notes = arrayListOf<CharSequence>()

    override fun addNote(note: NoteMessage) {
        notes += note.text
    }

    fun build(): ValidationMessage = ValidationMessageImpl(
        kind = kind,
        contents = contents,
        notes = notes.toList(),
    )

    private data class ValidationMessageImpl(
        override val contents: CharSequence,
        override val kind: ValidationMessage.Kind,
        override val notes: Collection<CharSequence>,
    ) : ValidationMessage
}

// Constructors for the message text types are marked as internal to force all text to be written inside the
//  current module, so it doesn't clutter the "good-path" code.

@JvmInline
value class ErrorMessage internal constructor(val text: CharSequence)

internal fun CharSequence.toError(): ErrorMessage = ErrorMessage(this)

@JvmInline
value class WarningMessage internal constructor(val text: CharSequence)

internal fun CharSequence.toWarning(): WarningMessage = WarningMessage(this)

@JvmInline
value class NoteMessage internal constructor(val text: CharSequence)

internal fun CharSequence.toNote(): NoteMessage = NoteMessage(this)