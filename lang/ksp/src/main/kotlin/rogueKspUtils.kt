package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.impl.binary.KSPropertyDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeImpl
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.types.RawType

internal fun KSType.isRaw(): Boolean {
    // FIXME: Remove this ksp-impl workaround when fix is available for the public api.
    return when(this) {
        is KSTypeImpl -> kotlinType.unwrap() is RawType
        else -> Utils.resolver.isJavaRawType(this)
    }
}

internal fun KSPropertyDeclaration.isLateInit(): Boolean {
    // FIXME: Remove this ksp-impl workaround when fix is available for the public api.
    return when(this) {
        is KSPropertyDeclarationDescriptorImpl -> descriptor.isLateInit
        else -> Modifier.LATEINIT in modifiers
    }
}

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