package com.yandex.yatagan.validation

public interface RichString : CharSequence {
    /**
     * @return plain string, discarding all "rich" info.
     */
    override fun toString(): String

    /**
     * @return rich string representation with ANSI control sequences.
     */
    public fun toAnsiEscapedString(): String
}
