/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl.parsing

import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.buildRichString

internal class ParseException(
    source: String,
    where: IntRange,
    what: String,
) : RuntimeException() {
    constructor(
        source: String,
        where: Int,
        what: String,
    ) : this(
        source = source,
        where = where..where,
        what = what
    )

    val formattedMessage: RichString = buildRichString {
        // Normalize whitespace characters to ' ' plain spaces, so they don't interfere with the formatting.
        val normalizedSource = source.replace("\\s".toRegex(), " ")
        appendRichString { append('"').also { color = TextColor.Gray } }
        appendRichString { append(normalizedSource, 0, where.first) }
        appendRichString { append(normalizedSource, where.first, where.last).also { color = TextColor.BrightMagenta } }
        appendRichString { append(normalizedSource, where.last, normalizedSource.length) }
        appendRichString { append('"').also { color = TextColor.Gray } }
        appendLine()

        val left = where.first - what.length - " ~~~^".length
        if (left < 1) {
            repeat(where.first + 1) { append(' ') }
            append('^')
            if (where.last > where.first) {
                repeat(where.last - where.first) { append('~') }
                append('^')
            }
            append("~~~ ")
            append(what)
        } else {
            repeat(left + 2) { append(' ') }
            append(what).append(" ~~~^")
            if (where.last > where.first) {
                repeat(where.last - where.first) { append('~') }
                append('^')
            }
        }
    }

    override val message: String = what
}