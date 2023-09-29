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

import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.ValidationMessage
import kotlin.math.max


fun LocatedMessage.format(
    maxEncounterPaths: Int = Int.MAX_VALUE,
    encounterColor: TextColor = TextColor.Blue,
    referenceColor: TextColor = TextColor.Cyan,
    includeReferences: Boolean = true,
): RichString = buildRichString {
    val messageKindColor = when (message.kind) {
        ValidationMessage.Kind.Error -> TextColor.Red
        ValidationMessage.Kind.MandatoryWarning -> TextColor.Magenta
        ValidationMessage.Kind.Warning -> TextColor.Yellow
    }
    appendRichString {
        append(message.contents)
        color = messageKindColor
    }
    appendLine()
    for (note in message.notes) {
        appendRichString {
            append("NOTE: ")
            append(note)
            color = TextColor.BrightBlue
            isBold = false
        }
        appendLine()
    }
    var reference: Int? = null
    encounterPaths.asSequence().take(maxEncounterPaths).forEachIndexed { pathIndex, path ->
        if (pathIndex != 0) {
            appendLine()
        }
        appendRichString {
            color = encounterColor
            appendLine("Encountered:")
        }
        var referenceId = 1
        path.forEachIndexed { index, pathElement: CharSequence ->
            val prefix = buildRichString {
                color = encounterColor
                if (index == path.lastIndex) {
                    append("  here: ")
                } else {
                    append("  in ")
                }
                if (reference != null) {
                    appendRichString {
                        color = referenceColor
                        append("[").append(reference).append("*] ")
                    }
                }
            }
            appendRichString {
                color = if (reference != null) referenceColor else encounterColor
                isBold = false
                append(prefix)
            }
            appendLine(pathElement)
            val referenceRange = (pathElement as? RichStringImpl)?.computeChildContextRange()
            if (includeReferences && referenceRange != null && index != path.lastIndex) {
                // Append a reference decoration
                reference = referenceId++
                appendRichString {
                    color = referenceColor
                    append(" ".repeat(referenceRange.first + prefix.length))
                    append("^-")
                    val mention = "[*$reference]"
                    append(mention)
                    append("-".repeat(max(referenceRange.last - referenceRange.first - mention.length - 1, 1)))
                }
                appendLine()
            } else {
                reference = null
            }
            if (includeReferences && index == path.lastIndex) {
                appendRichString {
                    color = messageKindColor
                    append(" ".repeat(prefix.length))
                    append("^")
                    append("~".repeat(max(pathElement.length - 1, 1)))
                }
                appendLine()
            }
        }
    }
    if (encounterPaths.size > maxEncounterPaths) {
        val hiddenCount = encounterPaths.size - maxEncounterPaths
        appendRichString {
            color = TextColor.Gray
            isBold = true
            append("... $hiddenCount more encounter path(s) were not displayed.")
        }
    }
}
