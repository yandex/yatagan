package com.yandex.daggerlite.compiler

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import javax.lang.model.element.Element

inline fun <reified T : Annotation> Element.qualify(): JavaxQualifier? = annotationMirrors
    .filter { it.annotationType.asElement().isAnnotatedWith<T>() }
    .let {
        if (it.size > 1) {
            throw RuntimeException("Class $simpleName has more that one qualifier")
        }

        if (it.isEmpty()) null else JavaxQualifier(
            buildString {
                val annotation = it.first()
                append(annotation.annotationType.toString())
                annotation.elementValues.entries.joinTo(this, separator = "$", prefix = ":") { entry ->
                    "${entry.key.simpleName}_${entry.value}"
                }
            }
        )
    }

data class JavaxQualifier(val tag: String) : NodeModel.Qualifier, ProvisionBinding.Scope {
    override fun toString() = tag
}