package com.yandex.daggerlite.validation

/**
 * Validation message, issued by [MayBeInvalid].
 * Should have reasonable [equals]/[hashCode] implementation for grouping.
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
    }

    /**
     * Validation message Kind.
     */
    val kind: Kind

    /**
     * Message text.
     */
    val contents: String

    /**
     * Notes, related to the message, helping/clarifying/adding more info.
     */
    val notes: Collection<String>
}