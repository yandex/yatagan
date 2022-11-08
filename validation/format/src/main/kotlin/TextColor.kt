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