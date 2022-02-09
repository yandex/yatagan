package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

class KspModelFactoryImpl : LangModelFactory {
    private val listDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(List::class.java.canonicalName)) {
        "Not reached: unable to define list declaration"
    }
    private val collectionDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(Collection::class.java.canonicalName)) {
        "Not reached: unable to define collection declaration"
    }

    override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        with(Utils.resolver) {
            val reference = createKSTypeReferenceFromKSType((type as KspTypeImpl).impl)
            val argument = getTypeArgument(reference, if (isCovariant) Variance.COVARIANT else Variance.INVARIANT)
            return KspTypeImpl(listDeclaration.asType(listOf(argument)))
        }
    }

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        with(Utils.resolver) {
            val reference = createKSTypeReferenceFromKSType((type as KspTypeImpl).impl)
            val argument = getTypeArgument(reference, Variance.INVARIANT)
            return KspTypeImpl(collectionDeclaration.asType(listOf(argument)))
        }
    }

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel {
        val declaration = checkNotNull(Utils.resolver.getClassDeclarationByName(qualifiedName)) {
            "Type $qualifiedName is not found"
        }
        return KspTypeDeclarationImpl(declaration.asType(emptyList()))
    }

    override val errorType: TypeLangModel
        get() = KspTypeImpl(ErrorTypeImpl)
}
