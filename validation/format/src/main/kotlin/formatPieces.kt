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

@file:[JvmMultifileClass JvmName("Format") ]
package com.yandex.yatagan.validation.format

import com.yandex.yatagan.validation.RichString

internal val OpenBracket = buildRichString {
    color = TextColor.Gray
    append('[')
}

internal val CloseBracket = buildRichString {
    color = TextColor.Gray
    append(']')
}

internal val LogicalAnd = buildRichString {
    color = TextColor.Gray
    append(" && ")
}

internal val LogicalOr = buildRichString {
    color = TextColor.Gray
    append(" || ")
}

internal val OpenParenthesis = buildRichString {
    color = TextColor.Gray
    append('(')
}

internal val CloseParenthesis = buildRichString {
    color = TextColor.Gray
    append(')')
}

internal val Newline: RichString = RichStringImpl(
    children = listOf("\n"),
    color = TextColor.Default,
    isBold = false,
    isChildContext = false,
)

val Unresolved = buildRichString {
    color = TextColor.Red
    append("<undefined>")
}

val Negation = buildRichString {
    color = TextColor.Cyan
    append('!')
}
