package com.yandex.daggerlite.jap

import com.yandex.daggerlite.process.Logger
import javax.annotation.processing.Messager
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.MANDATORY_WARNING

class JapLogger(private val messager: Messager) : Logger {
    override fun error(message: String) {
        messager.printMessage(ERROR, message)
    }

    override fun warning(message: String) {
        messager.printMessage(MANDATORY_WARNING, message)
    }
}