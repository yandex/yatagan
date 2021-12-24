package com.yandex.daggerlite.process

class LoggerDecorator(
    private val wrapped: Logger,
) : Logger {
    override fun error(message: String) {
        wrapped.error(decorateError(message))
    }

    override fun warning(message: String) {
        wrapped.warning(decorateWarning(message))
    }

    companion object {
        fun decorateError(message: String): String {
            return ">>>[error]\n$message\n>>>"
        }

        fun decorateWarning(message: String): String {
            return ">>>[warning]\n$message\n>>>"
        }

        val MessageRegex = """>>>\[(warning|error)]\n(.*?)\n(.*?)\n\s*?>>>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}