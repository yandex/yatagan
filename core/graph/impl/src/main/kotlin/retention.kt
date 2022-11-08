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
            add(scope.annotationClass)
        }

        for (binding in graph.localBindings.keys) {
            for (scope in binding.scopes) {
                add(scope.annotationClass)
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