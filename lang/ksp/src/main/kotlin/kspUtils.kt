package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPrivate
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
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import com.yandex.daggerlite.core.lang.ParameterLangModel


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

internal fun KSPropertyDeclaration.isLateInit(): Boolean {
    return Modifier.LATEINIT in modifiers
}

internal fun KSPropertyDeclaration.isKotlinField(): Boolean {
    val modifiers = modifiers
    return Modifier.CONST in modifiers || isAnnotationPresent<JvmField>()
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

internal fun KSType.resolveAliasIfNeeded(): KSType = when (val declaration = declaration) {
    is KSTypeAlias -> declaration.type.resolve().resolveAliasIfNeeded()
    else -> this
}

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find(KSClassDeclaration::isCompanionObject)

private fun KSFunctionDeclaration.isFromObject(): Boolean = when(simpleName.asString()) {
    "clone" -> Utils.resolver.mapToJvmSignature(this) == "()Ljava/lang/Object;"
    "equals" -> Utils.resolver.mapToJvmSignature(this) == "(Ljava/lang/Object;)Z"
    "finalize" -> Utils.resolver.mapToJvmSignature(this) == "()V"
    "getClass" -> Utils.resolver.mapToJvmSignature(this) == "()Ljava/lang/Class;"
    "hashCode" -> Utils.resolver.mapToJvmSignature(this) == "()I"
    "notify" -> Utils.resolver.mapToJvmSignature(this) == "()V"
    "notifyAll" -> Utils.resolver.mapToJvmSignature(this) == "()V"
    "toString" -> Utils.resolver.mapToJvmSignature(this) == "()Ljava/lang/String;"
    "wait" -> when(Utils.resolver.mapToJvmSignature(this)) {
       "()V", "(J)V", "(JI)V" -> true
       else -> false
    }
    else -> false
}

internal fun KSClassDeclaration.allNonPrivateFunctions(): Sequence<KSFunctionDeclaration> =
    getAllFunctions()
        .filter {
            !it.isConstructor() && !it.isPrivate() && !it.isFromObject()
        } + getDeclaredFunctions().filter {
        // Include static functions.
        it.functionKind == FunctionKind.STATIC && !it.isPrivate()
    }

internal fun KSClassDeclaration.allNonPrivateProperties(): Sequence<KSPropertyDeclaration> {
    return when (origin) {
//        Origin.JAVA_LIB, Origin.JAVA -> emptySequence()  // No properties in Java
        else /* kotlin */ -> getAllProperties().filter { !it.isPrivate() && !it.isKotlinField() }
    }
}

internal fun parametersSequenceFor(
    declaration: KSFunctionDeclaration,
    jvmMethodSignature: JvmMethodSignature,
    containing: KSType?,
) = sequence<ParameterLangModel> {
    val parameters = declaration.parameters
    val types = containing?.let { declaration.asMemberOf(it).parameterTypes }
    for (i in parameters.indices) {
        val parameter = parameters[i]
        yield(
            KspParameterImpl(
                impl = parameter,
                jvmSignatureSupplier = { jvmMethodSignature.parameterTypesSignatures?.get(i) },
                refinedTypeRef = parameter.type.run {
                    if (types != null) replaceType(types[i] ?: ErrorTypeImpl) else this
                },
            )
        )
    }
}


internal object ErrorTypeImpl : KSType {
    override val annotations get() = emptySequence<Nothing>()
    override val arguments get() = emptyList<Nothing>()
    override val declaration: KSDeclaration get() = ErrorDeclarationImpl
    override val isError: Boolean get() = true
    override val isFunctionType: Boolean get() = false
    override val isMarkedNullable: Boolean get() = false
    override val isSuspendFunctionType: Boolean get() = false
    override val nullability: Nullability get() = Nullability.NOT_NULL
    override fun isAssignableFrom(that: KSType): Boolean = false
    override fun isCovarianceFlexible(): Boolean = false
    override fun isMutabilityFlexible(): Boolean = false
    override fun makeNotNullable(): KSType = this
    override fun makeNullable(): KSType = this
    override fun replace(arguments: List<KSTypeArgument>): KSType = this
    override fun starProjection(): KSType = this
    val Reference get() = this.asReference()
}

private object ErrorDeclarationImpl : KSClassDeclaration {
    override val annotations get() = emptySequence<Nothing>()
    override val classKind: ClassKind get() = ClassKind.CLASS
    override val containingFile: Nothing? get() = null
    override val declarations get() = emptySequence<Nothing>()
    override val docString: Nothing? get() = null
    override val isActual: Boolean get() = false
    override val isCompanionObject: Boolean get() = false
    override val isExpect: Boolean get() = false
    override val location: Location get() = NonExistLocation
    override val modifiers: Set<Modifier> get() = emptySet()
    override val origin: Origin get() = Origin.SYNTHETIC
    override val packageName: KSName get() = Utils.resolver.getKSNameFromString("")
    override val parent: Nothing? get() = null
    override val parentDeclaration: Nothing? get() = null
    override val primaryConstructor: Nothing? get() = null
    override val qualifiedName: Nothing? get() = null
    override val simpleName: KSName get() = Utils.resolver.getKSNameFromString("<Error>")
    override val superTypes get() = emptySequence<Nothing>()
    override val typeParameters get() = emptyList<Nothing>()
    override fun asStarProjectedType() = ErrorTypeImpl
    override fun findActuals() = emptySequence<Nothing>()
    override fun findExpects() = emptySequence<Nothing>()
    override fun getAllFunctions() = emptySequence<Nothing>()
    override fun getAllProperties() = emptySequence<Nothing>()
    override fun getSealedSubclasses() = emptySequence<Nothing>()

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        check(typeArguments.isEmpty()) { "Not reached" }
        return ErrorTypeImpl
    }
}
