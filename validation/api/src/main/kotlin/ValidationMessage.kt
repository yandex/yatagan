package com.yandex.yatagan.validation

/**
 * Validation message, issued by [MayBeInvalid].
 * Should have reasonable [equals]/[hashCode] implementation for grouping.
 */
public interface ValidationMessage {
    /**
     * Validation message Kind.
     */
    public enum class Kind {
        /**
         * Fatal message, if any message of such kind is issued, a processing will be marked as failed.
         */
        Error,

        /**
         * Serious warning, that can be turned into error in "strict mode".
         */
        MandatoryWarning,

        /**
         * Warning message, non-fatal.
         */
        Warning,
    }

    /**
     * Validation message Kind.
     */
    public val kind: Kind

    /**
     * Message text. Maybe [RichString] or any other [CharSequence].
     */
    public val contents: CharSequence

    /**
     * Notes, related to the message, helping/clarifying/adding more info.
     */
    public val notes: Collection<CharSequence>
}