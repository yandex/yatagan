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

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind

internal fun ReflectType.equivalence() = TypeEquivalenceWrapper(this)

internal val ReflectMember.isStatic get() = ReflectModifier.isStatic(modifiers)

internal val ReflectMember.isPublicStaticFinal get() = modifiers.let {
    ReflectModifier.isStatic(it) && ReflectModifier.isFinal(it) && ReflectModifier.isPublic(it)
}

internal val ReflectMember.isAbstract get() = ReflectModifier.isAbstract(modifiers)

internal val Class<*>.isAbstract get() = ReflectModifier.isAbstract(modifiers)

internal val ReflectMember.isPublic get() = ReflectModifier.isPublic(modifiers)

internal val Class<*>.isPublic get() = ReflectModifier.isPublic(modifiers)

internal val ReflectMember.isPrivate get() = ReflectModifier.isPrivate(modifiers)

internal val Class<*>.isPrivate get() = ReflectModifier.isPrivate(modifiers)

/**
 * This API is available since JVM 1.8, yet for Android it's not that simple, so
 * perform a runtime check.
 */
private val isExecutableApiAvailable = try {
    with(ClassLoader.getSystemClassLoader()) {
        loadClass("java.lang.reflect.Executable")
        loadClass("java.lang.reflect.Parameter")
    }
    true
} catch (e: ClassNotFoundException) {
    false
}

internal fun ReflectMethod.getParameterCountCompat(): Int =
    if (isExecutableApiAvailable) parameterCount8() else parameterTypes.size

internal fun ReflectConstructor.getParameterCountCompat(): Int =
    if (isExecutableApiAvailable) parameterCount8() else parameterTypes.size

internal fun ReflectMethod.parameterNamesCompat(): Array<String> {
    return if (isExecutableApiAvailable) parameterNames8() else Array(parameterTypes.size) { index -> "p$index" }
}

internal fun ReflectConstructor.parameterNamesCompat(): Array<String> {
    return if (isExecutableApiAvailable) parameterNames8() else Array(parameterTypes.size) { index -> "p$index" }
}

internal fun ReflectType.asClass(): Class<*> = tryAsClass() ?: throw AssertionError("Not reached: not a class")

internal fun ReflectType.tryAsClass(): Class<*>? = when (this) {
    is Class<*> -> this
    is ReflectParameterizedType -> rawType.asClass()
    else -> null
}

internal fun ReflectType.isAssignableFrom(another: ReflectType): Boolean {
    return when(another) {
        is Class<*> -> isAssignableFrom(another.boxed())
        is ReflectParameterizedType -> isAssignableFrom(another)
        is ReflectGenericArrayType -> isAssignableFrom(another)
        else -> false  // NOTE: We don't care about wildcards here, they are not-reached
    }
}

internal fun ReflectType.isAssignableFrom(another: Class<*>): Boolean {
    return when(this) {
        is Class<*> -> boxed().isAssignableFrom(another)
        is ReflectParameterizedType -> rawType.isAssignableFrom(another)  // As raw type (unchecked)
        is ReflectGenericArrayType -> genericComponentType.isAssignableFrom(another)  // As raw type (unchecked)
        else -> false
    }
}

internal fun ReflectType.isAssignableFrom(another: ReflectGenericArrayType): Boolean {
    return when(this) {
        is ReflectGenericArrayType -> genericComponentType.isAssignableFrom(another.genericComponentType)
        else -> false
    }
}

internal fun ReflectType.isAssignableFrom(another: ReflectParameterizedType): Boolean {
    return when(this) {
        is ReflectParameterizedType -> isAssignableFrom(another)
        else -> false
    }
}

internal fun ReflectParameterizedType.isAssignableFrom(another: ReflectParameterizedType): Boolean {
    // Check if raw types are assignable
    if (!this.rawType.isAssignableFrom(another.rawType))
        return false

    val thisArgs = actualTypeArguments
    val thatArgs = another.actualTypeArguments
    if (thisArgs.size != thatArgs.size)
        return false

    for (i in thisArgs.indices) {
        val thisArg: ReflectType = thisArgs[i]
        val thatArg: ReflectType = thatArgs[i]

        if (thisArg.isAssignableFrom(thatArg)) {
            continue
        }

        if (thisArg !is ReflectWildcardType)
            return false

        val thisUpperBound = thisArg.upperBounds.firstOrNull() ?: Any::class.java
        if (thatArg is ReflectWildcardType) {
            val thatUpperBound = thatArg.upperBounds.firstOrNull() ?: Any::class.java
            if (!thisUpperBound.isAssignableFrom(thatUpperBound)) {
                return false
            }
        } else {
            if (!thisUpperBound.isAssignableFrom(thatArg)) {
                return false
            }
        }
    }

    return true
}

internal fun Class<*>.isFromKotlin() = isAnnotationPresent(Metadata::class.java)

internal fun ReflectType.formatString(): String = when (this) {
    is Class<*> -> canonicalName ?: "<unnamed>"
    is ReflectParameterizedType -> "${rawType.formatString()}<${actualTypeArguments.joinToString { it.formatString() }}>"
    is ReflectWildcardType -> when {
        lowerBounds.isNotEmpty() -> "? super ${lowerBounds.first().formatString()}"
        upperBounds.isNotEmpty() -> when(val upperBound = upperBounds.first()) {
            Any::class.java -> "?"  // No need to write `<? extends java.langObject>`
            else -> "? extends ${upperBound.formatString()}"
        }
        else -> "?"
    }
    is ReflectGenericArrayType -> "${genericComponentType.formatString()}[]"
    is ReflectTypeVariable -> "<unresolved-type-var: ${name}>"
    else -> toString()
}

fun TypeDeclaration.kotlinObjectInstanceOrNull(): Any? {
    val model = this as RtTypeDeclarationImpl
    val impl = model.type.impl.asClass()
    return when(model.kind) {
        TypeDeclarationKind.KotlinObject -> impl.declaredFields.first { it.name == "INSTANCE" }
        TypeDeclarationKind.KotlinCompanion -> {
            val companionName = impl.simpleName
            impl.enclosingClass.declaredFields.first { it.name == companionName }
        }
        else -> null
    }?.get(null)
}

/**
 * @param declaringClass class that directly contains [this type][ReflectType] in its declaration.
 * @param asMemberOf type declaration, that this type was obtained from.
 */
internal fun ReflectType.resolveGenericsHierarchyAware(
    declaringClass: Class<*>,
    asMemberOf: RtTypeDeclarationImpl,
): ReflectType {
    return asMemberOf.typeHierarchy.find {
        // Find a type declaration that directly declares the type.
        it.type.impl.asClass() == declaringClass
    }?.genericsInfo?.let(::resolveGenerics) ?: this
}

private val RtTypeDeclarationImpl.typeHierarchy: Sequence<RtTypeDeclarationImpl>
    get() = sequence {
        yield(this@typeHierarchy)
        for (superType in superTypes) {
            if (superType !is RtTypeDeclarationImpl)
                continue
            yield(superType)
            yieldAll(superType.typeHierarchy)
        }
    }

internal fun ReflectType.resolveGenerics(genericsInfo: Lazy<Map<ReflectTypeVariable, ReflectType>>?): ReflectType {
    if (genericsInfo == null) {
        return this
    }
    return when (this) {
        is ReflectTypeVariable -> genericsInfo.value[this] ?: this
        is ReflectParameterizedType -> replaceTypeArguments { argument ->
            argument.resolveGenerics(genericsInfo)
        }
        is ReflectWildcardType -> replaceBounds { bound ->
            bound.resolveGenerics(genericsInfo)
        }
        is ReflectGenericArrayType -> replaceComponentType(
            componentType = genericComponentType.resolveGenerics(genericsInfo)
        )
        else -> this
    }
}

private fun ReflectGenericArrayType.replaceComponentType(
    componentType: ReflectType,
): ReflectType = when(componentType) {
    genericComponentType -> this
    is Class<*> -> ArraysReflectionUtils.newInstance(componentType, 0).javaClass
    else -> @Suppress("ObjectLiteralToLambda") object : ReflectGenericArrayType {
        override fun getGenericComponentType(): ReflectType = componentType
    }
}

private fun ReflectParameterizedType.replaceTypeArguments(
    actualTypeArguments: Array<ReflectType>,
) = object : ReflectParameterizedType by this {
    override fun getActualTypeArguments() = actualTypeArguments
}

private fun ReflectWildcardType.replaceBounds(
    lowerBounds: Array<ReflectType>,
    upperBounds: Array<ReflectType>,
) = object : ReflectWildcardType by this {
    override fun getLowerBounds(): Array<ReflectType> = lowerBounds
    override fun getUpperBounds(): Array<ReflectType> = upperBounds
}

private inline fun ReflectParameterizedType.replaceTypeArguments(transform: (ReflectType) -> ReflectType): ReflectParameterizedType {
    var changed = false
    val new = Array(actualTypeArguments.size) { i ->
        val old = actualTypeArguments[i]
        val new = transform(old)
        changed = changed || old !== new
        new
    }
    return if (changed) replaceTypeArguments(new) else this
}

private inline fun ReflectWildcardType.replaceBounds(transform: (ReflectType) -> ReflectType): ReflectWildcardType {
    var changed = false
    val lowerBounds = lowerBounds
    val upperBounds = upperBounds
    val newLowerBounds = Array(lowerBounds.size) { i ->
        val old = lowerBounds[i]
        val new = transform(old)
        changed = changed || old !== new
        new
    }
    val newUpperBounds = Array(upperBounds.size) { i ->
        val old = upperBounds[i]
        val new = transform(old)
        changed = changed || old !== new
        new
    }
    return if (changed) replaceBounds(
        lowerBounds = newLowerBounds,
        upperBounds = newUpperBounds,
    ) else this
}

fun Class<*>.boxed(): Class<*> {
    @Suppress("RemoveRedundantQualifierName")
    return if (!this.isPrimitive) this else when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> throw AssertionError("Not reached")
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST", "RemoveRedundantQualifierName")
internal inline val <reified A : kotlin.Annotation> A.javaAnnotationClass: Class<A>
    get() = (this as java.lang.annotation.Annotation).annotationType() as Class<A>

internal fun Class<*>.getAllFields(): List<ReflectField> = buildList {
    tailrec fun handleClass(clazz: Class<*>, includeStatic: Boolean) {
        if (clazz === Any::class.java) {
            // Do not add fields from java.lang.Object.
            return
        }
        for (declaredField in clazz.declaredFields) {
            if (declaredField.isPrivate || declaredField.isSynthetic) {
                // Skip private/synthetic fields
                continue
            }
            if (!includeStatic && declaredField.isStatic) {
                // Skip static fields
                continue
            }
            add(declaredField)
        }
        val superclass = clazz.superclass ?: return
        handleClass(clazz = superclass, includeStatic = false)
    }
    handleClass(clazz = this@getAllFields, includeStatic = true)
    sortBy { it.name }
}

internal fun Class<*>.getMethodsOverrideAware(): List<ReflectMethod> = buildList {
    val overrideControl = hashSetOf<MethodSignatureEquivalenceWrapper>()
    fun handleClass(clazz: Class<*>) {
        if (clazz === Any::class.java) {
            // Do not add methods from java.lang.Object.
            return
        }
        for (declaredMethod in clazz.declaredMethods) {
            if (declaredMethod.isPrivate || declaredMethod.isSynthetic) {
                // Skip private/synthetic methods
                continue
            }
            if (declaredMethod.isStatic || overrideControl.add(declaredMethod.signatureEquivalence())) {
                add(declaredMethod)
            }
        }
        clazz.superclass?.let(::handleClass)
        clazz.interfaces.forEach(::handleClass)
    }
    handleClass(this@getMethodsOverrideAware)
    sortWith(MethodSignatureComparator)
}

private fun arrayHashCode(types: Array<ReflectType>): Int = types.fold(1) { hash, type -> 31 * hash + hashCode(type) }

private fun hashCode(type: ReflectType): Int = when (type) {
    is ReflectParameterizedType -> 31 * type.rawType.hashCode() + arrayHashCode(type.actualTypeArguments)
    is ReflectWildcardType -> 31 * arrayHashCode(type.lowerBounds) + arrayHashCode(type.upperBounds)
    is ReflectGenericArrayType -> hashCode(type.genericComponentType)
    else -> type.hashCode()
}

private fun arrayEquals(one: Array<ReflectType>, other: Array<ReflectType>): Boolean {
    if (one.size != other.size) return false
    for (i in one.indices) {
        if (!equals(one[i], other[i]))
            return false
    }
    return true
}

private fun equals(one: ReflectType, other: ReflectType): Boolean = with(one) {
    when (this) {
        is ReflectParameterizedType -> {
            // Take synthetic parameterized types into account.
            other is ReflectParameterizedType &&
                    rawType == other.rawType && arrayEquals(actualTypeArguments, other.actualTypeArguments)
        }
        is ReflectWildcardType -> {
            // Take synthetic wildcard types into account
            other is ReflectWildcardType && arrayEquals(upperBounds, other.upperBounds) &&
                    arrayEquals(lowerBounds, other.lowerBounds)
        }
        is ReflectGenericArrayType -> {
            other is ReflectGenericArrayType && equals(genericComponentType, other.genericComponentType)
        }
        other -> true
        else -> false
    }
}

/**
 * This is required as [ReflectType.equals] works only with its internal implementations on some platforms.
 */
internal class TypeEquivalenceWrapper private constructor(private val type: ReflectType) {
    override fun hashCode(): Int = hashCode(type)
    override fun equals(other: Any?): Boolean {
        if (other !is TypeEquivalenceWrapper) return false
        return equals(type, other.type)
    }

    companion object Cache : ObjectCache<ReflectType, TypeEquivalenceWrapper>() {
        operator fun invoke(type: ReflectType) = createCached(type, ::TypeEquivalenceWrapper)
    }
}
