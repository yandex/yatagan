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

package com.yandex.yatagan.testing.source_set

import org.intellij.lang.annotations.Language

internal class SourceSetImpl : SourceSet {
    private val _sourceFiles: MutableList<SourceFile> = arrayListOf()

    override val sourceFiles: List<SourceFile> get() = _sourceFiles.toList()

    override fun givenJavaSource(
        name: String,
        @Language("java") source: String,
        addPackageDirective: Boolean,
    ) {
        _sourceFiles += if ('.' in name) {
            SourceFile.java(
                qName = name,
                code = if (addPackageDirective) "package ${name.substringBeforeLast('.')};\n" + source else source,
            )
        } else {
            SourceFile.java(
                qName = "$name.java",
                code = source,
            )
        }
    }

    override fun givenKotlinSource(
        name: String,
        @Language("kotlin") source: String,
        addPackageDirective: Boolean,
    ) {
        _sourceFiles += if ('.' in name) {
            SourceFile.kotlin(
                filePath = "${name.substringAfterLast('.')}.kt",
                code = if (addPackageDirective) "package ${name.substringBeforeLast('.')}\n" + source else source,
            )
        } else {
             SourceFile.kotlin(
                filePath = "$name.kt",
                code = source,
            )
        }
    }

    override fun includeFromSourceSet(sourceSet: SourceSet) {
        _sourceFiles += sourceSet.sourceFiles
    }
}
