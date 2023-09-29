/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.validation.format

import com.yandex.yatagan.validation.RichString

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

