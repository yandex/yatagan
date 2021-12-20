package com.yandex.daggerlite.validation

/**
 * Validation message, issued by [MayBeInvalid].
 * Should have reasonable [equals]/[hashCode] implementation.
 */
interface ValidationMessage {
    /**
     * Validation message Kind.
     */
    enum class Kind {
        /**
         * Fatal message, if any message of such kind is issued, a processing will be marked as failed.
         */
        Error,

        /**
         * Warning message, non-fatal.
         */
        Warning,

        /**
         * Usually good for additional notes/advices, explaining the error/warning.
         */
        Note,
    }

    /**
     * Validation message Kind.
     */
    val kind: Kind

    /**
     * Message payload.
     * TODO: String is too inflexible for message.
     */
    val contents: String

    /**
     * Nested messages, usually [Notes][Kind.Note].
     */
    val nestedMessages: Collection<ValidationMessage>
}