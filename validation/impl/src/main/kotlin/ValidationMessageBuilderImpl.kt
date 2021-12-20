package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.ValidationMessage

@PublishedApi
internal class ValidationMessageBuilderImpl(
    private val kind: ValidationMessage.Kind,
) : ValidationMessageBuilder {
    private val nested = arrayListOf<ValidationMessage>()

    override lateinit var contents: String

    override fun add(message: ValidationMessage) {
        nested += message
    }

    fun build(): ValidationMessage {
        return ValidationMessageImpl(
            kind = kind,
            contents = contents,
            nestedMessages = nested,
        )
    }

    private data class ValidationMessageImpl(
        override val kind: ValidationMessage.Kind,
        override val contents: String,
        override val nestedMessages: Collection<ValidationMessage>,
    ) : ValidationMessage
}