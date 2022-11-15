package com.yandex.yatagan.processor.jap

import com.google.auto.common.BasicAnnotationProcessor
import com.yandex.yatagan.processor.common.Options
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion

@SupportedSourceVersion(SourceVersion.RELEASE_8)
class JapYataganProcessor : BasicAnnotationProcessor() {
    override fun getSupportedOptions(): Set<String> {
        return setOf(
            Options.StrictMode.key,
            Options.MaxIssueEncounterPaths.key,
            Options.UsePlainOutput.key,
        )
    }

    override fun steps(): Iterable<Step> = with(processingEnv) {
        listOf(
            JapComponentProcessingStep(
                messager = messager,
                filer = filer,
                types = typeUtils,
                elements = elementUtils,
                options = processingEnv.options,
            ),
        )
    }
}