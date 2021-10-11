package com.yandex.dagger3.generator

interface GenerationLogger {
    fun error(message: String)
    fun warning(message: String)
}