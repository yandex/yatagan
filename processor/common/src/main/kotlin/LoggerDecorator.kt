package com.yandex.yatagan.processor.common

class LoggerDecorator(
    private val wrapped: Logger,
) : Logger {
    private val lock = Any()

    override fun error(message: String) {
        synchronized(lock) {
            wrapped.error(decorateError(message))
        }
    }

    override fun warning(message: String) {
        synchronized(lock) {
            wrapped.warning(decorateWarning(message))
        }
    }

    companion object {
        fun decorateError(message: String): String {
            return ">>>[error]\n$message\n>>>"
        }

        fun decorateWarning(message: String): String {
            return ">>>[warning]\n$message\n>>>"
        }

        val MessageRegex = """>>>\[(warning|error)]\n(.*?)>>>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}