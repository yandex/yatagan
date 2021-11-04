package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CompileTimeAnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope

internal class KspAnnotationImpl(
    private val impl: KSAnnotation,
) : CompileTimeAnnotationLangModel {
    private val descriptor by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            append(ClassNameModel(impl.annotationType.resolve()))
            append('(')
            impl.arguments.joinTo(this, separator = ",") {
                "${it.name?.asString()}=${it.value}"
            }
            append(')')
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
        return impl[attribute] as Boolean
    }

    override fun getTypes(attribute: String): Sequence<TypeLangModel> {
        @Suppress("UNCHECKED_CAST")
        return impl[attribute].let {
            when (it) {
                is List<*> -> (it as List<KSType>)
                // In java annotations we can create lists implicitly (e.g. @Component(modules = MyModule.class)).
                else -> listOf(it as KSType)
            }.asSequence().map(::KspTypeImpl).memoize()
        }
    }

    override fun getType(attribute: String): TypeLangModel {
        return KspTypeImpl((impl[attribute] as KSType))
    }

    override fun getString(attribute: String): String {
        return impl[attribute] as String
    }

    override fun getAnnotations(attribute: String): Sequence<CompileTimeAnnotationLangModel> {
        @Suppress("UNCHECKED_CAST")
        return (impl[attribute] as List<KSAnnotation>).asSequence().map(::KspAnnotationImpl).memoize()
    }

    override fun getAnnotation(attribute: String): CompileTimeAnnotationLangModel {
        return KspAnnotationImpl(impl[attribute] as KSAnnotation)
    }

    override fun equals(other: Any?): Boolean {
        // TODO: further optimize this
        return this === other || (other is KspAnnotationImpl && descriptor == other.descriptor)
    }

    override fun hashCode() = descriptor.hashCode()

    override fun toString() = descriptor
}
