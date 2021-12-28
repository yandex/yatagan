package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.ValidationMessage

@PublishedApi
internal class ValidationMessageBuilderImpl(
    private val kind: ValidationMessage.Kind,
) : ValidationMessageBuilder {
    private val notes = arrayListOf<String>()

    override lateinit var contents: String

    override fun addNote(note: String) {
        notes += note
    }

    fun build(): ValidationMessage {
        return ValidationMessageImpl(
            kind = kind,
            contents = contents,
            notes = notes,
        )
    }

    private data class ValidationMessageImpl(
        override val kind: ValidationMessage.Kind,
        override val contents: String,
        override val notes: Collection<String>,
    ) : ValidationMessage
}