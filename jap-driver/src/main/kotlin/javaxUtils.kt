package com.yandex.daggerlite.jap

import com.yandex.daggerlite.generator.ComponentGeneratorFacade
import com.yandex.daggerlite.generator.Language
import java.lang.RuntimeException
import javax.annotation.processing.Filer

fun ComponentGeneratorFacade.generateFile(filer: Filer) {
    if (targetLanguage != Language.Java)
        throw RuntimeException("Jap driver supports only java files generating")

    val canonicalName = "$targetPackageName.$targetClassName"
    val file = filer.createSourceFile(canonicalName)
    file.openWriter().use(::generateTo)
}