package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.LazyThreadSafetyMode.NONE

internal class KspAnnotationImpl(
    private val impl: KSAnnotation,
) : CtAnnotationLangModel {
    // NOTE: It seems there's no reasonable way to cache KspAnnotationImpl instances
    // as KSAnnotation has no sane `equals` implementation.
    // TODO: maybe invent something

    private val descriptor by lazy(NONE) {
        buildString {
            append('@')
            append(CtTypeNameModel(impl.annotationType.resolve()))
            if (impl.arguments.isNotEmpty()) {
                append('(')
                impl.arguments.joinTo(this, separator = ",") {
                    val value = when (val value = it.value) {
                        is String -> "\"$value\""
                        else -> value.toString()
                    }
                    when (val name = it.name?.asString()) {
                        null, "value" -> value
                        else -> "$name=$value"
                    }
                }
                append(')')
            }
        }
    }

    override val isScope: Boolean
        get() = impl.annotationType.resolve().declaration.isAnnotationPresent<Scope>()

    override val isQualifier: Boolean
        get() = impl.annotationType.resolve().declaration.isAnnotationPresent<Qualifier>()

    override fun <A : Annotation> hasType(type: Class<A>): Boolean {
        return impl.shortName.getShortName() == type.simpleName &&
                impl.annotationType.resolve().declaration.qualifiedName?.asString() == type.canonicalName
    }

    override fun getBoolean(attribute: String): Boolean {
        return impl[attribute] as? Boolean ?: false
    }

    override fun getTypes(attribute: String): Sequence<TypeLangModel> {
        @Suppress("UNCHECKED_CAST")
        return impl[attribute].let { value ->
            when (value) {
                is List<*> -> (value as List<KSType>).asSequence()
                null -> emptySequence()
                // In java annotations we can create lists implicitly (e.g. @Component(modules = MyModule.class)).
                else -> sequenceOf(value as KSType)
            }.map { KspTypeImpl(it) }.memoize()
        }
    }

    override fun getType(attribute: String): TypeLangModel {
        return KspTypeImpl((impl[attribute] as? KSType ?: ErrorTypeImpl))
    }

    override fun getString(attribute: String): String {
        return impl[attribute] as? String ?: "<error-annotation-attribute>"
    }

    override fun getAnnotations(attribute: String): Sequence<CtAnnotationLangModel> {
        return when(val value = impl[attribute]) {
            is List<*> -> value.asSequence().map { KspAnnotationImpl(it as KSAnnotation) }.memoize()
            is KSAnnotation -> sequenceOf(KspAnnotationImpl(value))
            else -> emptySequence()
        }
    }

    override fun getAnnotation(attribute: String): CtAnnotationLangModel {
        return KspAnnotationImpl(impl[attribute] as KSAnnotation)
    }

    override fun equals(other: Any?): Boolean {
        // TODO: further optimize this
        return this === other || (other is KspAnnotationImpl && descriptor == other.descriptor)
    }

    override fun hashCode() = descriptor.hashCode()

    override fun toString() = descriptor
}
