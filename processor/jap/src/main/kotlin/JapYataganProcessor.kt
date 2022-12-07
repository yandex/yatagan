/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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