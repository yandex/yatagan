package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.reportError

fun validateAnnotationsRetention(graph: BindingGraph, validator: Validator) {
    if (LangModelFactory.isInRuntimeEnvironment) {
        // No need to check retention in RT - non-runtime will not be visible anyway.
        return
    }

    // Gather scopes, qualifiers and map-keys, that must have RUNTIME retention, so RT can read them.
    val allAnnotationDeclarations: Set<AnnotationDeclarationLangModel> = buildSet {
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