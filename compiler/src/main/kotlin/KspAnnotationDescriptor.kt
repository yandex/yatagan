package com.yandex.dagger3.compiler

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.NodeModel

data class KspAnnotationDescriptor(val tag: String) : NodeModel.Qualifier, Binding.Scope {
    companion object {
        fun describe(annotation: KSAnnotation): KspAnnotationDescriptor {
            val descriptor = buildString {
                append(annotation.annotationType.resolve().declaration.nameBestEffort)
                annotation.arguments.joinTo(this, separator = "$") {
                    "${it.name?.asString()}_${it.value}"
                }
            }
            return KspAnnotationDescriptor(descriptor)
        }

        internal inline fun <reified A : Annotation> describeIfAny(annotated: KSAnnotated): KspAnnotationDescriptor? {
            return annotated.annotations.find {
                it.annotationType.resolve().declaration.getAnnotation<A>() != null
            }?.let(KspAnnotationDescriptor::describe)
        }
    }

    override fun toString() = tag
}

private val KSDeclaration.nameBestEffort: String
    get() = qualifiedName?.asString() ?: (packageName.asString() + simpleName.asString())