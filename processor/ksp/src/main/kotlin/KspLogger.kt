package com.yandex.yatagan.processor.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.yandex.yatagan.processor.common.Logger

internal class KspLogger(
    private val logger: KSPLogger,
) : Logger {
    override fun error(message: String) {
        logger.error(message /*TODO: support where*/)
    }

    override fun warning(message: String) {
        logger.warn(message /*TODO: support where*/)
    }
}