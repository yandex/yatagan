package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.compiled.CtConstructorBase
import com.yandex.yatagan.lang.compiled.CtFieldBase
import com.yandex.yatagan.lang.compiled.CtTypeDeclarationBase
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class KspTypeDeclarationImpl private constructor(
    val type: KspTypeImpl,
) : CtTypeDeclarationBase() {
    private val impl: KSClassDeclaration = type.impl.declaration as KSClassDeclaration
    private val annotated = KspAnnotatedImpl(impl)

    override val annotations: Sequence<CtAnnotationBase> = annotated.annotations
    override fun <A : Annotation> isAnnotatedWith(type: Class<A>) = annotated.isAnnotatedWith(type)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val isAbstract: Boolean
        get() = impl.isAbstract()

    override val kind: TypeDeclarationKind
        get() = when(impl.classKind) {
            ClassKind.INTERFACE -> TypeDeclarationKind.Interface
            ClassKind.CLASS -> TypeDeclarationKind.Class
            ClassKind.ENUM_CLASS -> TypeDeclarationKind.Enum
            ClassKind.ENUM_ENTRY -> TypeDeclarationKind.Enum  // TODO(*) WTF is with KSP?
            ClassKind.OBJECT -> when {
                impl.isCompanionObject && impl.simpleName.asString() == "Companion" -> TypeDeclarationKind.KotlinCompanion
                else -> TypeDeclarationKind.KotlinObject
            }
            ClassKind.ANNOTATION_CLASS -> TypeDeclarationKind.Annotation
        }

    override val qualifiedName: String
        get() = impl.qualifiedName?.asString() ?: ""

    override val enclosingType: TypeDeclaration?
        get() = (impl.parentDeclaration as? KSClassDeclaration)?.let { Factory(KspTypeImpl(it.asType(emptyList()))) }

    override val interfaces: Sequence<Type> by lazy {
        impl.superTypes.map {
            it.resolve()
        }.filter {
            it.classDeclaration()?.classKind != ClassKind.CLASS
        }.map { KspTypeImpl(it.asMemberOfThis()) }
            .memoize()
    }

    override val superType: Type? by lazy {
        impl.superTypes.map {
            it.resolve()
        }.find {
            val declaration = it.classDeclaration()
            declaration?.classKind == ClassKind.CLASS && declaration != Utils.anyType
        }?.let { KspTypeImpl(it.asMemberOfThis()) }
    }

    override val constructors: Sequence<Constructor> by lazy {
        when(kind) {
            TypeDeclarationKind.Annotation -> {
                // Kotlin treats annotations as classes, we don't.
                emptySequence()
            }
            else -> impl.getConstructors()
                .filter { !it.isPrivate() }
                .map { ConstructorImpl(platformModel = it) }
                .memoize()
        }
    }

    private interface FunctionFilter {
        fun filterFunction(func: KSFunctionDeclaration): Boolean = filterAll(func)
        fun filterProperty(prop: KSPropertyDeclaration): Boolean = filterAll(prop)
        fun filterAccessor(accessor: KSPropertyAccessor): Boolean = filterAll(accessor)
        fun filterAll(it: KSAnnotated) = true
    }

    override val methods: Sequence<Method> by lazy {
        sequence {
            when (kind) {
                TypeDeclarationKind.KotlinObject -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {},
                        isStatic = { it.isAnnotationPresent<JvmStatic>() }
                    )
                }
                TypeDeclarationKind.KotlinCompanion -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {
                            override fun filterAll(it: KSAnnotated): Boolean {
                                // Skip jvm-static methods in companion
                                return !it.isAnnotationPresent<JvmStatic>()
                            }
                        },
                        isStatic = { false }
                    )
                }
                else -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {},
                        isStatic = { it is KSModifierListOwner && Modifier.JAVA_STATIC in it.modifiers }
                    )
                    impl.getCompanionObject()?.let { companion ->
                        functionsImpl(
                            declaration = companion,
                            filter = object : FunctionFilter {
                                // Include jvm static from companion
                                override fun filterFunction(func: KSFunctionDeclaration): Boolean {
                                    return func.isAnnotationPresent<JvmStatic>()
                                }

                                override fun filterProperty(prop: KSPropertyDeclaration): Boolean {
                                    return true
                                }

                                override fun filterAccessor(accessor: KSPropertyAccessor): Boolean {
                                    return accessor.isAnnotationPresent<JvmStatic>() ||
                                            accessor.receiver.isAnnotationPresent<JvmStatic>()
                                }
                            },
                            isStatic = { true },
                        )
                    }
                }
            }
        }.memoize()
    }

    private suspend fun SequenceScope<Method>.functionsImpl(
        declaration: KSClassDeclaration,
        filter: FunctionFilter,
        isStatic: (KSAnnotated) -> Boolean,
    ) {
        for (method in declaration.allNonPrivateFunctions()) {
            if (!filter.filterFunction(method)) continue
            yield(KspMethodImpl(
                owner = this@KspTypeDeclarationImpl,
                impl = method,
                isStatic = isStatic(method),
            ))
        }

        for (property in declaration.allNonPrivateProperties()) {
            if (!filter.filterProperty(property)) continue
            explodeProperty(
                property = property,
                owner = this@KspTypeDeclarationImpl,
                filter = filter,
                isStatic = isStatic,
            )
        }
    }

    private suspend fun SequenceScope<Method>.explodeProperty(
        property: KSPropertyDeclaration,
        owner: KspTypeDeclarationImpl,
        filter: FunctionFilter,
        isStatic: (KSAnnotated) -> Boolean,
    ) {
        val isPropertyStatic = isStatic(property)
        property.getter?.let { getter ->
            if (filter.filterAccessor(getter)) {
                yield(KspPropertyGetterImpl(
                    owner = owner, getter = getter,
                    isStatic = isPropertyStatic || isStatic(getter)
                ))
            }
        }
        property.setter?.let { setter ->
            val modifiers = setter.modifiers
            if (Modifier.PRIVATE !in modifiers && Modifier.PROTECTED !in modifiers && filter.filterAccessor(setter)) {
                yield(KspPropertySetterImpl(
                    owner = owner, setter = setter,
                    isStatic = isPropertyStatic || isStatic(setter)
                ))
            }
        }
    }

    private suspend fun SequenceScope<Field>.yieldInheritedFields() {
        val declared = impl.getDeclaredProperties().toSet()

        // We can't use `getAllProperties()` here, as it doesn't handle Java's "shadowed" fields properly.
        suspend fun SequenceScope<Field>.includeFieldsFrom(type: KSType) {
            val clazz = type.declaration as? KSClassDeclaration ?: return
            clazz.getDeclaredProperties()
                .filter {
                    !it.isPrivate() && ((it.getter == null && it.setter == null &&
                            Modifier.JAVA_STATIC !in it.modifiers) /* Java field */ ||
                            it.isLateInit() /* lateinit property always exposes a field */) && it !in declared
                }
                .forEach { property ->
                    yield(
                        KspFieldImpl(
                            impl = property,
                            owner = this@KspTypeDeclarationImpl,
                            refinedOwner = type,
                            isStatic = false,
                        )
                    )
                }
            clazz.getSuperclass()?.let { includeFieldsFrom(it) }
        }
        includeFieldsFrom(type.impl)
    }

    override val fields: Sequence<Field> = run {
        sequence {
            when (kind) {
                TypeDeclarationKind.KotlinObject -> {
                    for (property in impl.getDeclaredProperties()) {
                        // `lateinit` generates exposed field
                        if (property.isPrivate() || (!property.isKotlinFieldInObject() && !property.isLateInit())) {
                            continue  // Not a field
                        }
                        yield(KspFieldImpl(
                                impl = property,
                                owner = this@KspTypeDeclarationImpl,
                                isStatic = true,  // Every field is static in kotlin object.
                        ))
                    }
                    // Simulate INSTANCE field for static kotlin singleton.
                    yield(PSFSyntheticField(
                            owner = this@KspTypeDeclarationImpl,
                            name = "INSTANCE",
                    ))
                }
                TypeDeclarationKind.KotlinCompanion -> {
                    // Nothing here, no fields are actually generated in companion,
                    //  they are all generated in the enclosing class.
                }
                else -> {
                    when (impl.origin) {
                        Origin.JAVA, Origin.JAVA_LIB -> {
                            // Then any "property" represents a field in Java
                            for (field in impl.getDeclaredProperties()) {
                                if (field.isPrivate()) continue
                                yield(KspFieldImpl(
                                    impl = field,
                                    owner = this@KspTypeDeclarationImpl,
                                    isStatic = Modifier.JAVA_STATIC in field.modifiers,
                                ))
                            }
                        }
                        Origin.KOTLIN, Origin.KOTLIN_LIB, Origin.SYNTHETIC -> {
                            // Assume kotlin origin.
                            for (property in impl.getDeclaredProperties()) {
                                // Only `lateinit` properties expose a field in regular kotlin class
                                if (property.isPrivate() || !property.isLateInit()) {
                                    continue
                                }
                                yield(KspFieldImpl(
                                    impl = property,
                                    owner = this@KspTypeDeclarationImpl,
                                    isStatic = false,
                                ))
                            }
                            impl.getCompanionObject()?.let { companion ->
                                // Include fields from companion (if any) as they are generated as static fields
                                //  in the enclosing class.
                                for (property in companion.getDeclaredProperties()) {
                                    if (property.isPrivate() ||
                                        (!property.isKotlinFieldInObject() && !property.isLateInit())) {
                                        continue
                                    }
                                    yield(KspFieldImpl(
                                        impl = property,
                                        owner = this@KspTypeDeclarationImpl,
                                        isStatic = true,  // Every field is static in companion
                                    ))
                                }
                                // Simulate companion object instance field.
                                yield(PSFSyntheticField(
                                    owner = this@KspTypeDeclarationImpl,
                                    type = KspTypeImpl(companion.asType(emptyList())),
                                    name = companion.simpleName.asString(),
                                ))
                            }
                        }
                    }
                }
            }
            yieldInheritedFields()
        }.memoize()
    }

    override val nestedClasses: Sequence<TypeDeclaration> by lazy {
        impl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { !it.isPrivate() }
            .map { Factory(KspTypeImpl(it.asType(emptyList()))) }
            .memoize()
    }

    override val defaultCompanionObjectDeclaration: KspTypeDeclarationImpl? by lazy {
        impl.getCompanionObject()?.takeIf {
            it.simpleName.asString() == "Companion"
        }?.let { companion ->
            KspTypeDeclarationImpl(KspTypeImpl(companion.asType(emptyList())))
        }
    }

    override fun asType(): Type {
        return type
    }

    override val platformModel: KSClassDeclaration
        get() = impl

    private val genericsInfo: Map<String, KSTypeArgument> by lazy(PUBLICATION) {
        impl.typeParameters.map { it.name.asString() }
            .zip(type.impl.arguments)
            .toMap()
    }

    private fun KSType.asMemberOfThis(): KSType {
        return when(val declaration = declaration) {
            is KSTypeParameter -> {
                genericsInfo[declaration.name.asString()]?.type?.resolve() ?: this
            }
            is KSClassDeclaration -> {
                if (arguments.isEmpty()) {
                    this
                } else declaration.asType(arguments.map { arg ->
                    when (val reference = arg.type) {
                        null -> arg
                        else -> {
                            val oldType = reference.resolve()
                            when (val newType = oldType.asMemberOfThis()) {
                                oldType -> arg
                                else -> Utils.resolver.getTypeArgument(reference.replaceType(newType), arg.variance)
                            }
                        }
                    }
                })
            }
            else -> this
        }
    }

    companion object Factory : ObjectCache<KspTypeImpl, KspTypeDeclarationImpl>() {
        operator fun invoke(impl: KspTypeImpl) =
            createCached(impl, ::KspTypeDeclarationImpl)
    }

    private inner class ConstructorImpl(
        override val platformModel: KSFunctionDeclaration,
    ) : CtConstructorBase(), Annotated by KspAnnotatedImpl(platformModel) {
        private val jvmSignature = JvmMethodSignature(platformModel)

        override val isEffectivelyPublic: Boolean
            get() {
                if (platformModel.origin == Origin.SYNTHETIC) {
                    val constructeeOrigin = this@KspTypeDeclarationImpl.platformModel.origin
                    if (constructeeOrigin == Origin.JAVA || constructeeOrigin == Origin.JAVA_LIB) {
                        // Java synthetic constructor has the same visibility as the class.
                        return this@KspTypeDeclarationImpl.isEffectivelyPublic
                    }
                }
                return platformModel.isPublicOrInternal()
            }
        override val constructee: TypeDeclaration get() = this@KspTypeDeclarationImpl
        override val parameters: Sequence<Parameter> = parametersSequenceFor(
            declaration = platformModel,
            containing = type.impl,
            jvmMethodSignature = jvmSignature,
        )
    }

    private class PSFSyntheticField(
        override val owner: TypeDeclaration,
        override val type: Type = owner.asType(),
        override val name: String,
    ) : CtFieldBase() {
        override val isEffectivelyPublic: Boolean get() = true
        override val annotations: Sequence<Nothing> get() = emptySequence()
        override val platformModel: Any? get() = null
        override val isStatic: Boolean get() = true
    }
}