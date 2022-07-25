package com.yandex.daggerlite.validation.format

import com.yandex.daggerlite.validation.RichString

/**
 * Use [buildRichString] functions to build [RichString]s.
 */
class RichStringBuilder @PublishedApi internal constructor(): Appendable {
    private val children = arrayListOf<CharSequence>()
    private val plainStringBuilder = StringBuilder()

    internal var isChildContext: Boolean = false

    /**
     * Text color to use.
     */
    var color: TextColor = TextColor.Default

    /**
     * Whether the text is displayed as bold (sometimes, in bright color).
     */
    var isBold: Boolean = false

    override fun append(part: CharSequence?): RichStringBuilder = apply {
        when (part) {
            is RichString -> appendDirect(part)
            null -> plainStringBuilder.append("null")
            else -> {
                val lines = part.split('\n')
                lines.forEachIndexed { index, line ->
                    if (index == lines.lastIndex) {
                        // Last line has to go buffered, as there can be new plain chars coming after it.
                        plainStringBuilder.append(line)
                    } else {
                        // Not-last line can go directly as a whole, as it is immediately followed by a rich string
                        appendDirect(line)
                        // This is required to reset attributes on newlines
                        appendDirect(Newline)
                    }
                }
            }
        }
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): RichStringBuilder {
        return append((csq ?: "null").subSequence(start, end))
    }

    override fun append(c: Char): RichStringBuilder = apply {
        when (c) {
            '\n' -> appendDirect(Newline)
            else -> plainStringBuilder.append(c)
        }
    }

    fun append(rich: RichString): RichStringBuilder = apply {
        appendDirect(rich)
    }

    private fun appendDirect(charSequence: CharSequence) {
        flushStringBuilder()
        children += charSequence
    }

    private fun flushStringBuilder() {
        with(plainStringBuilder) {
            if (isNotEmpty()) {
                children += toString()
                clear()
            }
        }
    }

    fun build(): RichString {
        flushStringBuilder()
        return RichStringImpl(
            children = children,
            color = color,
            isBold = isBold,
            isChildContext = isChildContext,
        )
    }
}

