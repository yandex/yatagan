/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.scope.LexicalScope

internal val LexicalScope.Utils: ProcessingUtils get() = ext[ProcessingUtils]

internal fun <A : Annotation> KSAnnotation.hasType(clazz: Class<A>): Boolean {
    return shortName.getShortName() == clazz.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() ==
            clazz.canonicalName
}

internal operator fun KSAnnotation.get(name: String): Any? {
    return arguments.find { (it.name?.asString() ?: "value") == name }?.value
}

internal inline fun <reified T : Annotation> KSAnnotated.isAnnotationPresent(): Boolean =
    isAnnotationPresent(T::class.java)

internal fun <T : Annotation> KSAnnotated.isAnnotationPresent(clazz: Class<T>): Boolean =
    annotations.any { it.hasType(clazz) }

internal fun KSPropertyDeclaration.isKotlinFieldInObject(): Boolean {
    return Modifier.CONST in modifiers || isAnnotationPresent<JvmField>()
}

internal fun KSPropertyDeclaration.isLateInit(): Boolean {
    return Modifier.LATEINIT in modifiers
}

internal fun KSDeclaration.isPublicOrInternal() = when (getVisibility()) {
    Visibility.PUBLIC, Visibility.INTERNAL -> true
    else -> false
}

internal val KSNode.isFromJava
    get() = when (origin) {
        Origin.JAVA, Origin.JAVA_LIB -> true
        else -> false
    }

internal val KSNode.isFromKotlin
    get() = when (origin) {
        Origin.KOTLIN, Origin.KOTLIN_LIB -> true
        else -> false
    }

internal val KSNode.isSynthetic
    get() = origin == Origin.SYNTHETIC

internal fun KSType.resolveAliasIfNeeded(): KSType = when (val declaration = declaration) {
    is KSTypeAlias -> declaration.type.resolve().resolveAliasIfNeeded()
    else -> this
}

internal fun KSType.classDeclaration(): KSClassDeclaration? = when (val declaration = declaration) {
    is KSTypeAlias -> declaration.type.resolve().classDeclaration()
    else -> declaration as? KSClassDeclaration
}

internal fun LexicalScope.isRaw(type: KSType): Boolean {
    return Utils.resolver.isJavaRawType(type)
}

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find(KSClassDeclaration::isCompanionObject)

private fun LexicalScope.isFromObject(function: KSFunctionDeclaration): Boolean = when(function.simpleName.asString()) {
    "clone" -> Utils.resolver.mapToJvmSignature(function) == "()Ljava/lang/Object;"
    "equals" -> Utils.resolver.mapToJvmSignature(function) == "(Ljava/lang/Object;)Z"
    "finalize" -> Utils.resolver.mapToJvmSignature(function) == "()V"
    "getClass" -> Utils.resolver.mapToJvmSignature(function) == "()Ljava/lang/Class;"
    "hashCode" -> Utils.resolver.mapToJvmSignature(function) == "()I"
    "notify" -> Utils.resolver.mapToJvmSignature(function) == "()V"
    "notifyAll" -> Utils.resolver.mapToJvmSignature(function) == "()V"
    "toString" -> Utils.resolver.mapToJvmSignature(function) == "()Ljava/lang/String;"
    "wait" -> when(Utils.resolver.mapToJvmSignature(function)) {
       "()V", "(J)V", "(JI)V" -> true
       else -> false
    }
    else -> false
}

internal fun LexicalScope.allNonPrivateFunctions(declaration: KSClassDeclaration): Sequence<KSFunctionDeclaration> =
    declaration.getAllFunctions()
        .filter {
            !it.isConstructor() && !it.isPrivate() && !isFromObject(it)
        } + declaration.getDeclaredFunctions().filter {
        // Include static functions.
        (it.functionKind == FunctionKind.STATIC || it.modifiers.contains(Modifier.JAVA_STATIC)) && !it.isPrivate()
    }

internal fun allNonPrivateProperties(declaration: KSClassDeclaration): Sequence<KSPropertyDeclaration> {
    return declaration.getAllProperties().filter { !it.isPrivate() && !it.isKotlinFieldInObject() }
}

internal fun KSClassDeclaration.getSuperclass(): KSType? {
    return superTypes
        .map { it.resolve().resolveAliasIfNeeded() }
        .find { (it.declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS }
}

internal fun LexicalScope.parametersSequenceFor(
    declaration: KSFunctionDeclaration,
    jvmMethodSignature: JvmMethodSignature,
    containing: KSType?,
) = sequence<Parameter> {
    val parameters = declaration.parameters
    val types = containing?.let { declaration.asMemberOf(it).parameterTypes }
    for (i in parameters.indices) {
        val parameter = parameters[i]
        yield(
            KspParameterImpl(
                lexicalScope = this@parametersSequenceFor,
                impl = parameter,
                jvmSignatureSupplier = { jvmMethodSignature.parameterTypesSignatures?.get(i) },
                refinedTypeRef = parameter.type.run {
                    types?.get(i)?.let { replaceType(it) } ?: this
                },
            )
        )
    }
}

internal fun Resolver.getKotlinClassByName(qualifiedName: KSName, forceMutable: Boolean): KSClassDeclaration? {
    // TODO: Remove this workaround when this is available in the public api.
    // https://github.com/google/ksp/pull/1201
    var name = mapJavaNameToKotlin(qualifiedName) ?: qualifiedName
    if (forceMutable) {
        val mutable = mapKotlinClassMutabilityMapping(name.asString())
        if (mutable != null) {
            name = getKSNameFromString(mutable)
        }
    }
    return getClassDeclarationByName(name)
}

private fun mapKotlinClassMutabilityMapping(maybeReadOnly: String) = when(maybeReadOnly) {
    // based on info from `org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap`.
    "kotlin.collections.Iterable" -> "kotlin.collections.MutableIterable"
    "kotlin.collections.Iterator" -> "kotlin.collections.MutableIterator"
    "kotlin.collections.Collection" -> "kotlin.collections.MutableCollection"
    "kotlin.collections.List" -> "kotlin.collections.MutableList"
    "kotlin.collections.Set" -> "kotlin.collections.MutableSet"
    "kotlin.collections.ListIterator" -> "kotlin.collections.MutableListIterator"
    "kotlin.collections.Map" -> "kotlin.collections.MutableMap"
    "kotlin.collections.Map.Entry" -> "kotlin.collections.MutableMap.MutableEntry"
    else -> null
}