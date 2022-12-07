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

import kotlin.test.Test
import kotlin.test.assertEquals

class RichStringTest {
    @Test
    fun `trivial rich string`() {
        val richString = buildRichString {
            append("normal")
        }

        assertEquals(
            expected = "normal/normal",
            actual = "${richString}/${richString.toAnsiEscapedString()}",
        )
    }

    @Test
    fun `simple rich string`() {
        val richString = buildRichString {
            color = TextColor.Green
            isBold = true
            append('B')
            append("old")
            append('G')
            append("reen")
        }
        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "\u001B[32;1mBoldGreen\u001B[0m",
            actual = richString.toAnsiEscapedString(),
        )
        assertEquals(expected = "BoldGreen", actual = richString.toString())
    }

    @Test
    fun `single nested rich string`() {
        val richString = buildRichString {
            append("Normal ")
            appendRichString {
                color = TextColor.BrightCyan
                isBold = true
                append("BoldBrightCyan")
            }
            append(" Normal")
        }

        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "Normal \u001B[96;1mBoldBrightCyan\u001B[39;22m Normal",
            actual = richString.toAnsiEscapedString(),
        )
        assertEquals(expected = "Normal BoldBrightCyan Normal", actual = richString.toString())
    }

    @Test
    fun `multiple sibling nested rich strings`() {
        val richString = buildRichString {
            append("Normal ")
            appendRichString {
                color = TextColor.Red
                isBold = true
                append("BoldRed")
            }
            appendRichString {
                color = TextColor.Magenta
                isBold = false
                append("JustMagenta")
            }
            append(" Normal")
        }

        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "Normal \u001B[31;1mBoldRed\u001B[35;22mJustMagenta\u001B[39m Normal",
            actual = richString.toAnsiEscapedString(),
        )
        assertEquals(expected = "Normal BoldRedJustMagenta Normal", actual = richString.toString())
    }

    @Test
    fun `multi-level nested rich strings`() {
        val richString = buildRichString {
            append("normal")
            appendRichString {
                color = TextColor.BrightYellow
                append("BrightYellow")
                appendRichString {
                    color = TextColor.Magenta
                    append("Magenta")
                    appendRichString {
                        color = TextColor.Magenta
                        isBold = true
                        append("MagentaBold")
                    }
                    appendRichString {
                        color = TextColor.Default
                        isBold = true
                        append("NormalBold")
                    }
                    appendRichString {
                        color = TextColor.Default
                        isBold = false
                        append("Normal")
                    }
                    append("Magenta")
                }
                append("BrightYellow")
            }
            append("normal")
        }

        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "normal\u001B[93mBrightYellow\u001B[35mMagenta\u001B[1mMagentaBold" +
                    "\u001B[39mNormalBold\u001B[22mNormal\u001B[35mMagenta\u001B[93mBrightYellow\u001B[39mnormal",
            actual = richString.toAnsiEscapedString(),
        )
        assertEquals(
            expected = "normalBrightYellowMagentaMagentaBoldNormalBoldNormalMagentaBrightYellownormal",
            actual = richString.toString(),
        )
    }

    @Test
    fun `rich string with newlines`() {
        val richString = buildRichString {
            append("normal")
            appendRichString {
                appendRichString {
                    color = TextColor.Cyan
                    isBold = false
                    append("Cyan")
                }
                append("Normal")
                appendRichString {
                    color = TextColor.Yellow
                    isBold = true
                    append("YellowBold")
                }
            }
            appendLine()
            appendRichString {
                color = TextColor.Red
                isBold = false
                append("Red")
            }
            append("normal")
            appendRichString {
                color = TextColor.Yellow
                isBold = true
                append("YellowBold")
            }
            appendLine()
            appendRichString {
                color = TextColor.Yellow
                isBold = true
                append("YellowBold")
            }
        }

        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "normalCyanNormalYellowBold\n" +
                    "RednormalYellowBold\n" +
                    "YellowBold",
            actual = richString.toString(),
        )
        assertEquals(
            expected = "normal\u001B[36mCyan\u001B[39mNormal\u001B[33;1mYellowBold\u001B[39;22m\n" +
                    "\u001B[31mRed\u001B[39mnormal\u001B[33;1mYellowBold\u001B[39;22m\n" +
                    "\u001B[33;1mYellowBold",
            actual = richString.toAnsiEscapedString(),
        )
    }

    @Test
    fun `inherit color`() {
        val richString = buildRichString {
            append("normal")
            appendRichString {
                color = TextColor.Cyan
                appendRichString {
                    color = TextColor.Inherit
                    isBold = false
                    append("InheritedCyan\n")
                    appendRichString {
                        color = TextColor.Inherit
                        isBold = false
                        append("InheritedCyan")
                    }
                }
                appendRichString {
                    color = TextColor.Yellow
                    isBold = true
                    append("YellowBold")
                }
                append("Cyan")
            }
            appendLine()
            appendRichString {
                color = TextColor.Red
                isBold = false
                append("Red")
            }
        }

        println(richString.toAnsiEscapedString())
        assertEquals(
            expected = "normalInheritedCyan\nInheritedCyanYellowBoldCyan\nRed",
            actual = richString.toString(),
        )
        assertEquals(
            expected = "normal\u001B[36mInheritedCyan\u001B[39m\n" +
                    "\u001B[36mInheritedCyan\u001B[33;1mYellowBold\u001B[36;22mCyan\u001B[39m\n" +
                    "\u001B[31mRed",
            actual = richString.toAnsiEscapedString(),
        )
    }
}