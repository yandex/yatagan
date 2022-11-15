package com.yandex.yatagan.rt.support

/**
 * An interface for Yatagan debug/info logging.
 */
interface Logger {
    /**
     * Called by the framework to log some info.
     *
     * @param message a framework provided message string.
     */
    fun log(message: String)
}