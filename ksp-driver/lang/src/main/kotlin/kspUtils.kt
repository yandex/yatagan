package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getJavaClassByName
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
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.base.memoize
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

internal fun mapToJavaPlatformIfNeeded(declaration: KSClassDeclaration): KSClassDeclaration {
    if (!declaration.packageName.asString().startsWith("kotlin")) {
        // Heuristic: only types from `kotlin` package can have different java counterparts.
        return declaration
    }

    @OptIn(KspExperimental::class)
    return declaration.qualifiedName?.let(Utils.resolver::getJavaClassByName) ?: declaration
}

internal fun mapToJavaPlatformIfNeeded(type: KSType): KSType {
    // TODO: deal with nullability here
    // MAYBE: Perf: implement caching for non-trivial mappings?
    val originalDeclaration = type.declaration as? KSClassDeclaration ?: return type
    val mappedDeclaration = mapToJavaPlatformIfNeeded(originalDeclaration)
    if (mappedDeclaration == originalDeclaration && type.arguments.isEmpty()) {
        return type
    }
    with(Utils.resolver) {
        return mappedDeclaration.asType(type.arguments.map {
            val originalArgType = it.type?.resolve() ?: return@map it
            val mappedArgType = originalArgType.let(::mapToJavaPlatformIfNeeded)
            if (mappedArgType != originalArgType) {
                // Replace the whole argument
                val ref = createKSTypeReferenceFromKSType(mappedArgType)
                getTypeArgument(ref, variance = Variance.INVARIANT)
            } else if (it.variance != Variance.INVARIANT) {
                // Replace variance - we don't use it here.
                getTypeArgument(it.type!!, variance = Variance.INVARIANT)
            } else it
        })
    }
}

internal fun CtTypeNameModel(declaration: KSClassDeclaration): CtTypeNameModel {
    val packageName = declaration.packageName.asString()
    return CtTypeNameModel(
        packageName = packageName,
        simpleNames = declaration.qualifiedName!!.asString().substring(startIndex = packageName.length + 1)
            .split('.'),
        typeArguments = emptyList(),
    )
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