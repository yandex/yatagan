package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import kotlin.reflect.KClass


internal fun <A : Annotation> KSAnnotation.hasType(clazz: KClass<A>): Boolean {
    return shortName.getShortName() == clazz.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() ==
            clazz.qualifiedName
}

internal operator fun KSAnnotation.get(name: String): Any? {
    return arguments.find { (it.name?.asString() ?: "value") == name }?.value
}

internal inline fun <reified T : Annotation> KSAnnotated.isAnnotationPresent(): Boolean =
    annotations.any { it.hasType(T::class) }

internal val KSDeclaration.isStatic get() = Modifier.JAVA_STATIC in modifiers || isAnnotationPresent<JvmStatic>()

internal val KSDeclaration.isObject get() = this is KSClassDeclaration && classKind == ClassKind.OBJECT

internal val KSPropertyDeclaration.isField
    get() = origin == Origin.JAVA || origin == Origin.JAVA_LIB || isAnnotationPresent<JvmField>()

internal fun CtTypeNameModel(declaration: KSClassDeclaration): CtTypeNameModel {
    val qualifiedName = declaration.qualifiedName!!
    val mappedJavaName = if (declaration.packageName.asString().startsWith("kotlin")) {
        @OptIn(KspExperimental::class)
        Utils.resolver.mapKotlinNameToJava(qualifiedName)?.asString()?.takeIf {
            // Do not use it the same as original.
            it != qualifiedName.asString()
        }
    } else null
    return if (mappedJavaName != null) {
        // We assume, that mapped Java type is not a nested type - only one simple name is present.
        // Otherwise, we would have to do `Utils.resolver.getJavaClassByName()` yet it seems like and overkill.
        CtTypeNameModel(
            packageName = mappedJavaName.substringBeforeLast('.'),
            simpleNames = listOf(mappedJavaName.substringAfterLast('.')),
            typeArguments = emptyList(),
        )
    } else {
        val packageName = declaration.packageName.asString()
        CtTypeNameModel(
            packageName = packageName,
            simpleNames = qualifiedName.asString().substring(startIndex = packageName.length + 1).split('.'),
            typeArguments = emptyList(),
        )
    }
}

internal fun CtTypeNameModel(type: KSType): CtTypeNameModel {
    return CtTypeNameModel(type.declaration as KSClassDeclaration)
        .withArguments(type.arguments.map { CtTypeNameModel(it.type!!.resolve()) })
}

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find(KSClassDeclaration::isCompanionObject)

internal fun KSClassDeclaration.allPublicFunctions() : Sequence<KSFunctionDeclaration> {
    return sequenceOf(
        getAllFunctions(),
        getDeclaredFunctions().filter(KSFunctionDeclaration::isStatic),
    ).flatten().filter(KSFunctionDeclaration::isPublic)
}

internal fun KSClassDeclaration.allPublicProperties() : Sequence<KSPropertyDeclaration> {
    return getAllProperties().filter(KSPropertyDeclaration::isPublic)
}

internal fun annotationsFrom(impl: KSAnnotated) = impl.annotations.map(::KspAnnotationImpl).memoize()