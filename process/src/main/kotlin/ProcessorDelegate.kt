package com.yandex.daggerlite.process

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import java.io.Writer

interface ProcessorDelegate<Source> {
    val logger: Logger

    fun createDeclaration(source: Source): TypeDeclarationLangModel

    fun openFileForGenerating(
        source: Source,
        packageName: String,
        className: String,
    ): Writer
}