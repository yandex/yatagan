package com.yandex.daggerlite.generator

interface GenerationLogger {
    fun error(message: String)
    fun warning(message: String)
}