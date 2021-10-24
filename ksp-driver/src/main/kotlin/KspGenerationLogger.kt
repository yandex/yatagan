package com.yandex.daggerlite.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.yandex.daggerlite.generator.GenerationLogger

internal class KspGenerationLogger(
    private val logger: KSPLogger,
) : GenerationLogger {
    override fun error(message: String) {
        logger.error(message /*TODO: support where*/)
    }

    override fun warning(message: String) {
        logger.warn(message /*TODO: support where*/)
    }
}