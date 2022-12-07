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

/**
 * Text color for rich strings.
 */
enum class TextColor(
    internal val ansiCode: String,
) {
    Default("39"),
    Inherit("39"),

    Black("30"),
    Red("31"),
    Green("32"),
    Yellow("33"),
    Blue("34"),
    Magenta("35"),
    Cyan("36"),
    White("37"),

    Gray("90"),  // Bright black
    BrightRed("91"),
    BrightGreen("92"),
    BrightYellow("93"),
    BrightBlue("94"),
    BrightMagenta("95"),
    BrightCyan("96"),
    BrightWrite("97"),
}