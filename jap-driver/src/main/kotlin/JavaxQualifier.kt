package com.yandex.daggerlite.jap

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import javax.lang.model.element.Element

inline fun <reified T : Annotation> Element.qualify(): JavaxQualifier? = annotationMirrors
    .find { it.annotationType.asElement().isAnnotatedWith<T>() }?.let {
        JavaxQualifier(
            buildString {
                append(it.annotationType.toString())
                it.elementValues.entries.joinTo(this, separator = "$", prefix = ":") { entry ->
                    "${entry.key.simpleName}_${entry.value}"
                }
            }
        )
    }

data class JavaxQualifier(val tag: String) : NodeModel.Qualifier, ProvisionBinding.Scope {
    override fun toString() = tag
}