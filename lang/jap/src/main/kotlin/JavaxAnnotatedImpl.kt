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

package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element

// See https://youtrack.jetbrains.com/issue/KT-51087, which was fixed in [1, 9, 0] metadata version.
private const val KT_51087_FIXED_METADATA_VERSION = 10_90_00

internal class JavaxAnnotatedImpl(
    private val impl: Element,
) : CtAnnotated {

    override val annotations: Sequence<CtAnnotationBase> by lazy {
        val annotations = impl.annotationMirrors.map { JavaxAnnotationImpl(it) }
        if (shouldReverseAnnotations()) {
            annotations.asReversed()
        } else {
            annotations
        }.asSequence()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return getAnnotationIfPresent(type) != null
    }

    private fun <A : Annotation> getAnnotationIfPresent(clazz: Class<A>): AnnotationMirror? {
        val annotationClassName = clazz.canonicalName
        for (annotationMirror in impl.annotationMirrors) {
            val annotationTypeElement = annotationMirror.annotationType.asTypeElement()
            if (annotationTypeElement.qualifiedName.contentEquals(annotationClassName)) {
                return annotationMirror
            }
        }
        return null
    }

    private fun shouldReverseAnnotations(): Boolean {
        val rawMetadataVersion: IntArray = impl.kotlinMetadataVersion() ?: return false
        // Means this is from KAPT, and it's known to be reversing annotations order.
        val (major, minor, patch) = rawMetadataVersion
        val metadataVersion = major * 10_00_00 + minor * 10_00 + patch
        return metadataVersion < KT_51087_FIXED_METADATA_VERSION
    }
}