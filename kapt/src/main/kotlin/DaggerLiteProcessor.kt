package com.yandex.daggerlite.compiler

import com.google.auto.common.BasicAnnotationProcessor
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion

@SupportedSourceVersion(SourceVersion.RELEASE_11)
class DaggerLiteProcessor : BasicAnnotationProcessor() {
    override fun steps(): Iterable<Step> = with(processingEnv) {
        listOf(
            ComponentProcessingStep(filer, typeUtils, elementUtils),
        )
    }
}