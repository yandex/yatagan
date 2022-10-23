package com.yandex.daggerlite.validation

data class LocatedMessage(
    /**
     * Message payload.
     */
    val message: ValidationMessage,

    /**
     * A list of encounter paths, where the [message] was reported.
     */
    val encounterPaths: List<List<CharSequence>>,
)