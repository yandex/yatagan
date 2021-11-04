package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
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

internal fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val qualifiedName = declaration.qualifiedName!!
    val mappedJavaName = if (declaration.packageName.asString().startsWith("kotlin")) {
        Utils.resolver.mapKotlinNameToJava(qualifiedName)?.asString()?.takeIf {
            // Do not use it the same as original.
            it != qualifiedName.asString()
        }
    } else null
    return if (mappedJavaName != null) {
        // We assume, that mapped Java type is not a nested type - only one simple name is present.
        // Otherwise, we would have to do `Utils.resolver.getJavaClassByName()` yet it seems like and overkill.
        ClassNameModel(
            packageName = mappedJavaName.substringBeforeLast('.'),
            simpleNames = listOf(mappedJavaName.substringAfterLast('.')),
            typeArguments = emptyList(),
        )
    } else {
        val packageName = declaration.packageName.asString()
        ClassNameModel(
            packageName = packageName,
            simpleNames = qualifiedName.asString().substring(startIndex = packageName.length + 1).split('.'),
            typeArguments = emptyList(),
        )
    }
}

internal fun ClassNameModel(type: KSType): ClassNameModel {
    return ClassNameModel(type.declaration as KSClassDeclaration)
        .withArguments(type.arguments.map { ClassNameModel(it.type!!.resolve()) })
}

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find { it.isCompanionObject }

internal fun KSClassDeclaration.allMemberFunctionsAndPropertiesModels(
    owner: TypeDeclarationLangModel,
): Sequence<FunctionLangModel> = sequenceOf(
    getAllProperties().map {
        KspFunctionPropertyGetterImpl(
            owner = owner,
            impl = it,
            isFromCompanionObject = this.isCompanionObject,
        )
    },
    getAllFunctions().map {
        KspFunctionImpl(
            owner = owner,
            impl = it,
            isFromCompanionObject = this.isCompanionObject,
        )
    },
    // [KSClassDeclaration.getAllFunctions()] returns only member functions, not including java static.
    if (!this.isObject) getDeclaredFunctions().filter { it.isStatic }.map {
        KspFunctionImpl(
            owner = owner,
            impl = it,
            isFromCompanionObject = this.isCompanionObject,
        )
    } else emptySequence()
).flatten()