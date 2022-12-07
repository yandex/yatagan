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

internal class RichStringImpl(
    val children: List<CharSequence>,
    override val color: TextColor,
    override val isBold: Boolean,
    val isChildContext: Boolean,
) : RichString, Style {
    override fun toString(): String = children.joinToString(separator = "")

    override fun toAnsiEscapedString(): String {
        return buildString(capacity = length + children.size * 5) {
            formatAnsiEscapedString(
                previousState = StyleStateImpl(),
                currentState = this@RichStringImpl,
                children = children,
            )
            if (!(color == TextColor.Default && !isBold)) {
                // Reset at the end of the string
                append(CSI).append("0m")
            }
        }
    }

    override val length: Int
        get() = children.sumOf { it.length }

    fun computeChildContextRange(): IntRange? {
        if (isChildContext) {
            return 0 until length
        }
        var startIndex = 0
        for (child in children) {
            if (child is RichStringImpl) {
                child.computeChildContextRange()?.let {
                    return it.first + startIndex..it.last + startIndex
                }
            }
            startIndex += child.length
        }
        return null
    }

    override fun get(index: Int): Char {
        if (index < 0)
            throw IndexOutOfBoundsException()
        var subIndex = index
        val iterator = children.iterator()
        while (iterator.hasNext()) {
            val child = iterator.next()
            val subLength = child.length
            if (subIndex < subLength)
                return child[subIndex]
            subIndex -= subLength
        }
        throw IndexOutOfBoundsException()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return toString().subSequence(startIndex, endIndex)
    }

    private class StyleStateImpl : StyleState {
        override var color: TextColor = TextColor.Default
        override var isBold: Boolean = false
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RichStringImpl &&
                color == other.color &&
                isBold == other.isBold &&
                isChildContext == other.isChildContext &&
                children == other.children)
    }

    override fun hashCode(): Int {
        var result = children.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + isBold.hashCode()
        result = 31 * result + isChildContext.hashCode()
        return result
    }
}

internal interface Style {
    val color: TextColor
    val isBold: Boolean
}

internal interface StyleState : Style {
    override var color: TextColor
    override var isBold: Boolean
}

private fun StringBuilder.formatAnsiEscapedString(
    previousState: StyleState,
    currentState: Style,
    children: List<CharSequence>,
) {
    fun StyleState.assign(state: Style) {
        color = state.color
        isBold = state.isBold
    }

    fun tryInheritFrom(new: Style, parent: Style): Style {
        return if (new.color == TextColor.Inherit) object : Style {
            override val color: TextColor = parent.color
            override val isBold: Boolean = new.isBold
        } else new
    }

    val needColorChange = previousState.color != currentState.color
    val needBoldChange = previousState.isBold != currentState.isBold

    if (needColorChange || needBoldChange) {
        append(CSI)
        if (needColorChange) {
            append(currentState.color.ansiCode)
        }
        if (needBoldChange) {
            if (needColorChange) {
                append(';')
            }
            append(if (currentState.isBold) BoldMode else NotBoldMode)
        }
        append("m")
    }

    previousState.assign(currentState)

    for (child in children) {
        when (child) {
            is RichStringImpl -> {
                formatAnsiEscapedString(
                    previousState = previousState,
                    currentState = tryInheritFrom(new = child, parent = currentState),
                    children = child.children,
                )
            }
            else -> {
                formatAnsiEscapedString(
                    previousState = previousState,
                    currentState = currentState,
                    children = emptyList(),
                )
                append(child)
            }
        }
    }
}

// https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters
private const val BoldMode = "1"
private const val NotBoldMode = "22"
private const val CSI = "\u001B["

