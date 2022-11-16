package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe


internal fun Resolver.getKotlinClassByName(qualifiedName: KSName, forceMutable: Boolean): KSClassDeclaration? {
    // FIXME: Remove this ksp-impl workaround when this is available in the public api.
    var name = mapJavaNameToKotlin(qualifiedName) ?: qualifiedName
    if (forceMutable) {
        val mutable = JavaToKotlinClassMap.readOnlyToMutable(FqNameUnsafe(name.asString()))
        if (mutable != null) {
            name = getKSNameFromString(mutable.asString())
        }
    }
    return getClassDeclarationByName(name)
}