package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.lang.TypeDeclarationKind
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

private typealias ArraysReflectionUtils = java.lang.reflect.Array

internal fun Type.equivalence() = TypeEquivalenceWrapper(this)

internal val Member.isStatic get() = Modifier.isStatic(modifiers)

internal val Member.isPublicStaticFinal get() = modifiers.let {
    Modifier.isStatic(it) && Modifier.isFinal(it) && Modifier.isPublic(it)
}

internal val Member.isAbstract get() = Modifier.isAbstract(modifiers)

internal val Member.isPublic get() = Modifier.isPublic(modifiers)

internal val Class<*>.isPublic get() = Modifier.isPublic(modifiers)

internal val Member.isPrivate get() = Modifier.isPrivate(modifiers)

internal val Class<*>.isPrivate get() = Modifier.isPrivate(modifiers)

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

internal fun Method.getParameterCountCompat(): Int =
    if (isExecutableApiAvailable) parameterCount8() else parameterTypes.size

internal fun Constructor<*>.getParameterCountCompat(): Int =
    if (isExecutableApiAvailable) parameterCount8() else parameterTypes.size

internal fun Method.parameterNamesCompat(): Array<String> {
    return if (isExecutableApiAvailable) parameterNames8() else Array(parameterTypes.size) { index -> "p$index" }
}

internal fun Constructor<*>.parameterNamesCompat(): Array<String> {
    return if (isExecutableApiAvailable) parameterNames8() else Array(parameterTypes.size) { index -> "p$index" }
}

internal fun Type.asClass(): Class<*> = tryAsClass() ?: throw AssertionError("Not reached: not a class")

internal fun Type.tryAsClass(): Class<*>? = when (this) {
    is Class<*> -> this
    is ParameterizedType -> rawType.asClass()
    else -> null
}

internal fun Type.isAssignableFrom(another: Type): Boolean {
    return when(another) {
        is Class<*> -> isAssignableFrom(another.boxed())
        is ParameterizedType -> isAssignableFrom(another)
        is GenericArrayType -> isAssignableFrom(another)
        else -> false  // NOTE: We don't care about wildcards here, they are not-reached
    }
}

internal fun Type.isAssignableFrom(another: Class<*>): Boolean {
    return when(this) {
        is Class<*> -> boxed().isAssignableFrom(another)
        is ParameterizedType -> rawType.isAssignableFrom(another)  // As raw type (unchecked)
        is GenericArrayType -> genericComponentType.isAssignableFrom(another)  // As raw type (unchecked)
        else -> false
    }
}

internal fun Type.isAssignableFrom(another: GenericArrayType): Boolean {
    return when(this) {
        is GenericArrayType -> genericComponentType.isAssignableFrom(another.genericComponentType)
        else -> false
    }
}

internal fun Type.isAssignableFrom(another: ParameterizedType): Boolean {
    return when(this) {
        is ParameterizedType -> isAssignableFrom(another)
        else -> false
    }
}

internal fun ParameterizedType.isAssignableFrom(another: ParameterizedType): Boolean {
    // Check if raw types are assignable
    if (!this.rawType.isAssignableFrom(another.rawType))
        return false

    val thisArgs = actualTypeArguments
    val thatArgs = another.actualTypeArguments
    if (thisArgs.size != thatArgs.size)
        return false

    for (i in thisArgs.indices) {
        val thisArg: Type = thisArgs[i]
        val thatArg: Type = thatArgs[i]

        if (thisArg.isAssignableFrom(thatArg)) {
            continue
        }

        if (thisArg !is WildcardType)
            return false

        val thisUpperBound = thisArg.upperBounds.firstOrNull() ?: Any::class.java
        if (thatArg is WildcardType) {
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

internal fun Type.formatString(): String = when (this) {
    is Class<*> -> canonicalName ?: "<unnamed>"
    is ParameterizedType -> "${rawType.formatString()}<${actualTypeArguments.joinToString { it.formatString() }}>"
    is WildcardType -> when {
        lowerBounds.isNotEmpty() -> "? super ${lowerBounds.first().formatString()}"
        upperBounds.isNotEmpty() -> when(val upperBound = upperBounds.first()) {
            Any::class.java -> "?"  // No need to write `<? extends java.langObject>`
            else -> "? extends ${upperBound.formatString()}"
        }
        else -> "?"
    }
    is GenericArrayType -> "${genericComponentType.formatString()}[]"
    is TypeVariable<*> -> "error.UnresolvedCla$$"
    else -> toString()
}

fun TypeDeclarationLangModel.kotlinObjectInstanceOrNull(): Any? {
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
 * @param declaringClass class that directly contains [this type][Type] in its declaration.
 * @param asMemberOf type declaration, that this type was obtained from.
 */
internal fun Type.resolveGenericsHierarchyAware(
    declaringClass: Class<*>,
    asMemberOf: RtTypeDeclarationImpl,
): Type {
    return asMemberOf.typeHierarchy.find {
        // Find a type declaration that directly declares the type.
        it.type.impl.asClass() == declaringClass
    }?.genericsInfo?.let(::resolveGenerics) ?: this
}

private val RtTypeDeclarationImpl.typeHierarchy: Sequence<RtTypeDeclarationImpl>
    get() = sequence {
        yield(this@typeHierarchy)
        for (superType in superTypes) {
            yield(superType)
            yieldAll(superType.typeHierarchy)
        }
    }

internal fun Type.resolveGenerics(genericsInfo: Lazy<Map<TypeVariable<*>, Type>>?): Type {
    if (genericsInfo == null) {
        return this
    }
    return when (this) {
        is TypeVariable<*> -> genericsInfo.value[this] ?: this
        is ParameterizedType -> replaceTypeArguments { argument ->
            argument.resolveGenerics(genericsInfo)
        }
        is WildcardType -> replaceBounds { bound ->
            bound.resolveGenerics(genericsInfo)
        }
        is GenericArrayType -> replaceComponentType(
            componentType = genericComponentType.resolveGenerics(genericsInfo)
        )
        else -> this
    }
}

private fun GenericArrayType.replaceComponentType(
    componentType: Type,
): Type = when(componentType) {
    genericComponentType -> this
    is Class<*> -> ArraysReflectionUtils.newInstance(componentType, 0).javaClass
    else -> @Suppress("ObjectLiteralToLambda") object : GenericArrayType {
        override fun getGenericComponentType(): Type = componentType
    }
}

private fun ParameterizedType.replaceTypeArguments(
    actualTypeArguments: Array<Type>,
) = object : ParameterizedType by this {
    override fun getActualTypeArguments() = actualTypeArguments
}

private fun WildcardType.replaceBounds(
    lowerBounds: Array<Type>,
    upperBounds: Array<Type>,
) = object : WildcardType by this {
    override fun getLowerBounds(): Array<Type> = lowerBounds
    override fun getUpperBounds(): Array<Type> = upperBounds
}

private inline fun ParameterizedType.replaceTypeArguments(transform: (Type) -> Type): ParameterizedType {
    var changed = false
    val new = Array(actualTypeArguments.size) { i ->
        val old = actualTypeArguments[i]
        val new = transform(old)
        changed = changed || old !== new
        new
    }
    return if (changed) replaceTypeArguments(new) else this
}

private inline fun WildcardType.replaceBounds(transform: (Type) -> Type): WildcardType {
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

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
internal inline val <reified A : Annotation> A.javaAnnotationClass: Class<A>
    get() = (this as java.lang.annotation.Annotation).annotationType() as Class<A>

internal fun Class<*>.getAllFields(): List<Field> = buildList {
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

internal fun Class<*>.getMethodsOverrideAware(): List<Method> = buildList {
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

private fun arrayHashCode(types: Array<Type>): Int = types.fold(1) { hash, type -> 31 * hash + hashCode(type) }

private fun hashCode(type: Type): Int = when (type) {
    is ParameterizedType -> 31 * type.rawType.hashCode() + arrayHashCode(type.actualTypeArguments)
    is WildcardType -> 31 * arrayHashCode(type.lowerBounds) + arrayHashCode(type.upperBounds)
    is GenericArrayType -> hashCode(type.genericComponentType)
    else -> type.hashCode()
}

private fun arrayEquals(one: Array<Type>, other: Array<Type>): Boolean {
    if (one.size != other.size) return false
    for (i in one.indices) {
        if (!equals(one[i], other[i]))
            return false
    }
    return true
}

private fun equals(one: Type, other: Type): Boolean = with(one) {
    when (this) {
        is ParameterizedType -> {
            // Take synthetic parameterized types into account.
            other is ParameterizedType &&
                    rawType == other.rawType && arrayEquals(actualTypeArguments, other.actualTypeArguments)
        }
        is WildcardType -> {
            // Take synthetic wildcard types into account
            other is WildcardType && arrayEquals(upperBounds, other.upperBounds) &&
                    arrayEquals(lowerBounds, other.lowerBounds)
        }
        is GenericArrayType -> {
            other is GenericArrayType && equals(genericComponentType, other.genericComponentType)
        }
        other -> true
        else -> false
    }
}

/**
 * This is required as [Type.equals] works only with its internal implementations on some platforms.
 */
internal class TypeEquivalenceWrapper private constructor(private val type: Type) {
    override fun hashCode(): Int = hashCode(type)
    override fun equals(other: Any?): Boolean {
        if (other !is TypeEquivalenceWrapper) return false
        return equals(type, other.type)
    }

    companion object Cache : ObjectCache<Type, TypeEquivalenceWrapper>() {
        operator fun invoke(type: Type) = createCached(type, ::TypeEquivalenceWrapper)
    }
}
