package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeLangModel

class KspModelFactoryImpl : LangModelFactory {
    private val listDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(List::class.java.canonicalName)) {
        "Not reached: unable to define list declaration"
    }

    override fun getAnnotation(clazz: Class<out Annotation>): AnnotationLangModel {
        return KspAnnotationImpl(FakeKsAnnotationImpl(
            checkNotNull(Utils.resolver.getClassDeclarationByName(clazz.canonicalName)) {
                "Not reached: unable to define $clazz annotation"
            }
        ))
    }

    override fun getListType(type: TypeLangModel): TypeLangModel {
        with(Utils.resolver) {
            val reference = createKSTypeReferenceFromKSType((type as KspTypeImpl).impl)
            val argument = getTypeArgument(reference, Variance.INVARIANT)
            return KspTypeImpl(listDeclaration.asType(listOf(argument)))
        }
    }

    private class FakeKsAnnotationImpl(
        annotationClassDeclaration: KSClassDeclaration,
    ) : KSAnnotation {
        override val arguments: List<KSValueArgument> get() = emptyList()
        override val shortName: KSName = annotationClassDeclaration.simpleName
        override val useSiteTarget: AnnotationUseSiteTarget? get() = null
        override val location: Location get() = NonExistLocation
        override val origin: Origin get() = Origin.SYNTHETIC
        override val parent: KSNode? get() = null

        override val annotationType: KSTypeReference =
            Utils.resolver.createKSTypeReferenceFromKSType(annotationClassDeclaration.asType(emptyList()))

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitAnnotation(this, data)
        }
    }
}
