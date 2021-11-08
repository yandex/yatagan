package com.yandex.daggerlite.process

interface Logger {
    fun error(message: String)
    fun warning(message: String)
}