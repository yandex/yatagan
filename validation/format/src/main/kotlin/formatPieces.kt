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
