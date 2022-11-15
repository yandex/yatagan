package com.yandex.yatagan.validation

public data class LocatedMessage(
    /**
     * Message payload.
     */
    val message: ValidationMessage,

    /**
     * A list of encounter paths, where the [message] was reported.
     */
    val encounterPaths: List<List<CharSequence>>,
)