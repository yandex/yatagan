package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

class KspAnnotationClassImpl private constructor(
    private val reference: KSTypeReference,
    private val shortName: String,
) : AnnotationDeclarationLangModel {
    private val annotated by lazy {
        KspAnnotatedImpl(reference.resolve().resolveAliasIfNeeded().declaration as KSClassDeclaration)
    }

    override val annotations: Sequence<AnnotationLangModel>
        get() = annotated.annotations

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotated.isAnnotatedWith(type)
    }

    override val attributes: Sequence<AnnotationDeclarationLangModel.Attribute> by lazy {
        annotated.impl.getDeclaredProperties().map {
            AttributeImpl(impl = it)
        }.memoize()
    }

    override fun isClass(clazz: Class<out Annotation>): Boolean {
        return shortName == clazz.simpleName &&
                annotated.impl.qualifiedName?.asString() == clazz.canonicalName
    }

    private class AttributeImpl(
        private val impl: KSPropertyDeclaration,
    ) : AnnotationDeclarationLangModel.Attribute {

        override val name: String
            get() = impl.simpleName.asString()

        override val type: TypeLangModel by lazy {
            KspTypeImpl(
                reference = impl.type,
                jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl)
            )
        }
    }

    companion object Factory : ObjectCache<Pair<KSTypeReference, String>, KspAnnotationClassImpl>() {
        operator fun invoke(
            annotation: KSAnnotation,
        ): KspAnnotationClassImpl {
            val shortName = annotation.shortName.getShortName()
            val reference = annotation.annotationType
            return createCached(reference to shortName) {
                KspAnnotationClassImpl(
                    reference = reference,
                    shortName = shortName,
                )
            }
        }
    }
}