package com.yandex.daggerlite.validation

interface RichString : CharSequence {
    /**
     * @return plain string, discarding all "rich" info.
     */
    override fun toString(): String

    /**
     * @return rich string representation with ANSI control sequences.
     */
    fun toAnsiEscapedString(): String
}
