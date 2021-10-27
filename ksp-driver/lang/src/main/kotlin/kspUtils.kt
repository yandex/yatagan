package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
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

private data class TypeName(val packageName: String, val qualifiedName: String)

// TODO: if we want to determine primitives, then probably we should do it here.
private fun KSDeclaration.resolveJavaTypeName(): TypeName {
    return when (qualifiedName!!.asString()) {
        Any::class.qualifiedName -> with(java.lang.Object::class.java) { TypeName(packageName, name) }
        else -> TypeName(packageName.asString(), qualifiedName!!.asString())
    }
}

internal fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val typeName = declaration.resolveJavaTypeName()
    // MAYBE: use KSName api instead of string manipulation.
    val names = typeName.qualifiedName
        .substring(startIndex = typeName.packageName.length + 1)
        .split('.')
    return ClassNameModel(
        packageName = typeName.packageName,
        simpleNames = names,
        typeArguments = emptyList(),
    )
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
            isFromCompanionObject = this.isCompanionObject
        )
    },
    getAllFunctions().map {
        KspFunctionImpl(
            owner = owner,
            it,
            isFromCompanionObject = this.isCompanionObject
        )
    },
    // [KSClassDeclaration.getAllFunctions()] returns only member functions, not including java static.
    if (!this.isObject) getDeclaredFunctions().filter { it.isStatic }.map {
        KspFunctionImpl(
            owner = owner,
            it,
            isFromCompanionObject = this.isCompanionObject
        )
    } else emptySequence()
).flatten()