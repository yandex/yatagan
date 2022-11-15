package com.yandex.yatagan.rt.support

/**
 * Implementation of [Logger] that prints messages to console/standard output and supports simple tagging.
 *
 * @param tag a string to prepend to every message.
 */
class ConsoleLogger(
    private val tag: String = "[YataganRt]",
) : Logger {
    override fun log(message: String) {
        println("$tag $message")
    }
}