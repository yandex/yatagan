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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

fun validateAnnotationsRetention(graph: BindingGraph, validator: Validator) {
    if (LangModelFactory.isInRuntimeEnvironment) {
        // No need to check retention in RT - non-runtime will not be visible anyway.
        return
    }

    // Gather scopes, qualifiers and map-keys, that must have RUNTIME retention, so RT can read them.
    val allAnnotationDeclarations: Set<AnnotationDeclaration> = buildSet {
        for (scope in graph.scopes) {
            scope.customAnnotationClass?.let(::add)
        }

        for (binding in graph.localBindings.keys) {
            for (scope in binding.scopes) {
                scope.customAnnotationClass?.let(::add)
            }
            binding.target.qualifier?.let { add(it.annotationClass) }
        }
        for (module in graph.modules) {
            for (bindingModel in module.bindings) {
                val target = bindingModel.target
                if (target is BindingTargetModel.MappingContribution) {
                    target.mapKeyClass?.let { add(it) }
                }
            }
        }
    }

    for (annotationDeclaration in allAnnotationDeclarations) {
        when(val actual = annotationDeclaration.getRetention()) {
            AnnotationRetention.RUNTIME -> { /* OK */ }
            else -> validator.reportError(Strings.Errors.invalidAnnotationRetention(
                annotationDeclaration = annotationDeclaration,
                insteadOf = actual,
            ))
        }
    }
}