package com.yandex.daggerlite.jap

import com.google.auto.common.BasicAnnotationProcessor
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion

@SupportedSourceVersion(SourceVersion.RELEASE_11)
class JapDaggerLiteProcessor : BasicAnnotationProcessor() {
    override fun steps(): Iterable<Step> = with(processingEnv) {
        listOf(
            JapComponentProcessingStep(
                messager = messager,
                filer = filer,
                types = typeUtils,
                elements = elementUtils,
            ),
        )
    }
}