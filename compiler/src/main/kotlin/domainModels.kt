package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.ConstructorNameModel
import com.yandex.dagger3.core.EntryPointModel
import com.yandex.dagger3.core.FunctionNameModel
import com.yandex.dagger3.core.ModuleModel
import com.yandex.dagger3.core.NameModel
import com.yandex.dagger3.core.NodeDependency
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.NodeQualifier
import com.yandex.dagger3.core.NodeScope
import com.yandex.dagger3.core.PropertyNameModel
import com.yandex.dagger3.core.ProvisionBinding
import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Provides
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.reflect.KClass

data class KspComponentModel(
    val componentDeclaration: KSClassDeclaration,
) : ComponentModel {
    override val name: NameModel = NameModel(componentDeclaration)

    private val impl = requireNotNull(componentDeclaration.getAnnotation<Component>()) {
        "declaration $componentDeclaration can't be represented by ComponentModel"
    }

    @Suppress("UNCHECKED_CAST")
    override val modules: Set<KspModuleModel> by lazy {
        val list = impl["modules"] as? List<KSType> ?: return@lazy emptySet()
        list.mapTo(hashSetOf(), ::KspModuleModel)
    }

    @Suppress("UNCHECKED_CAST")
    override val dependencies: Set<KspComponentModel> by lazy {
        val list = impl["dependencies"] as? List<KSType> ?: return@lazy emptySet()
        list.mapTo(hashSetOf()) { KspComponentModel(it.declaration as KSClassDeclaration) }
    }

    override val entryPoints: Set<EntryPointModel> by lazy {
        buildSet {
            for (function in componentDeclaration.getAllFunctions().filter { it.isAbstract }) {
                this += EntryPointModel(
                    dep = NodeDependency.resolveFromType(
                        type = function.returnType?.resolve() ?: continue,
                        forQualifier = function,
                    ),
                    getter = FunctionNameModel(componentDeclaration, function),
                )
            }
            for (prop in componentDeclaration.getAllProperties().filter { it.isAbstract() && !it.isMutable }) {
                this += EntryPointModel(
                    dep = NodeDependency.resolveFromType(type = prop.type.resolve(), forQualifier = prop),
                    getter = PropertyNameModel(componentDeclaration, prop),
                )
            }
        }
    }
}

data class KspAnnotationDescriptor(val tag: String) : NodeQualifier, NodeScope {
    companion object {
        fun describe(annotation: KSAnnotation): KspAnnotationDescriptor {
            val descriptor = buildString {
                append(annotation.annotationType.resolve().declaration.nameBestEffort)
                annotation.arguments.joinTo(this, separator = "$") {
                    "${it.name?.asString()}_${it.value}"
                }
            }
            return KspAnnotationDescriptor(descriptor)
        }

        internal inline fun <reified A : Annotation> describeIfAny(annotated: KSAnnotated): KspAnnotationDescriptor? {
            return annotated.annotations.find {
                it.annotationType.resolve().declaration.getAnnotation<A>() != null
            }?.let(KspAnnotationDescriptor::describe)
        }
    }

    override fun toString() = tag
}

data class KspNodeModel(
    val type: KSType,
    override val qualifier: KspAnnotationDescriptor?,
) : NodeModel {
    constructor(
        type: KSType,
        forQualifier: KSAnnotated,
    ) : this(
        type = type,
        qualifier = KspAnnotationDescriptor.describeIfAny<Qualifier>(forQualifier)
    )

    override val defaultBinding: Binding? by lazy {
        if (qualifier != null) null
        else when (val declaration = type.declaration) {
            is KSClassDeclaration -> declaration.getConstructors().find {
                it.isAnnotationPresent(Inject::class)
            }
            else -> null
        }?.let { injectConstructor ->
            ProvisionBinding(
                target = this,
                ownerType = type,
                methodDeclaration = injectConstructor,
                forScope = type.declaration,
            )
        }
    }

    override val name: NameModel by lazy {
        NameModel(type)
    }

    override fun toString() = "${qualifier ?: ""} $name"
}

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    methodDeclaration: KSFunctionDeclaration,
    method: KSFunction = methodDeclaration.asMemberOf(ownerType),
    forScope: KSAnnotated = methodDeclaration,
) = ProvisionBinding(
    target = target,
    provider = if (methodDeclaration.isConstructor()) {
        ConstructorNameModel(NameModel(ownerType))
    } else FunctionNameModel(ownerType, methodDeclaration),
    params = method.parameterTypes
        .zip(methodDeclaration.parameters)
        .map { (paramType, param) ->
            NodeDependency.resolveFromType(type = paramType!!, forQualifier = param)
        },
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(forScope),
)

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    propertyDeclaration: KSPropertyDeclaration,
) = ProvisionBinding(
    target = target,
    provider = PropertyNameModel(ownerType, propertyDeclaration),
    params = emptyList(),
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(propertyDeclaration),
)

fun NodeDependency.Companion.resolveFromType(
    type: KSType,
    forQualifier: KSAnnotated,
): NodeDependency {
    val kind = when (type.declaration.qualifiedName?.asString()) {
        Lazy::class.qualifiedName -> NodeDependency.Kind.Lazy
        Provider::class.qualifiedName -> NodeDependency.Kind.Provider
        else -> NodeDependency.Kind.Normal
    }
    return NodeDependency(
        node = KspNodeModel(
            type = when (kind) {
                NodeDependency.Kind.Normal -> type
                else -> type.arguments[0].type!!.resolve()
            },
            forQualifier = forQualifier,
        ),
        kind = kind,
    )
}

data class KspModuleModel(
    val type: KSType,
) : ModuleModel {
    override val bindings: Collection<Binding> by lazy {
        val list = arrayListOf<Binding>()
        val declaration = type.declaration as KSClassDeclaration
        declaration.getDeclaredProperties().mapNotNullTo(list) { propertyDeclaration ->
            val propertyType = propertyDeclaration.asMemberOf(type)
            val target = KspNodeModel(
                type = propertyType,
                forQualifier = propertyDeclaration,
            )
            propertyDeclaration.annotations.forEach { ann ->
                when {
                    ann sameAs Provides::class -> ProvisionBinding(
                        target = target,
                        ownerType = type,
                        propertyDeclaration = propertyDeclaration,
                    )
                    else -> null
                }?.let { return@mapNotNullTo it }
            }
            null
        }
        declaration.getDeclaredFunctions().mapNotNullTo(list) { methodDeclaration ->
            val method = methodDeclaration.asMemberOf(type)
            val target = KspNodeModel(
                type = method.returnType!!,
                forQualifier = methodDeclaration,
            )
            methodDeclaration.annotations.forEach { ann ->
                when {
                    ann sameAs Binds::class -> AliasBinding(
                        target = target,
                        source = KspNodeModel(
                            type = methodDeclaration.parameters.single().type.resolve(),
                            forQualifier = methodDeclaration.parameters.single(),
                        ),
                        scope = KspAnnotationDescriptor.describeIfAny<Scope>(methodDeclaration),
                    )
                    ann sameAs Provides::class -> ProvisionBinding(
                        target = target,
                        ownerType = type,
                        method = method,
                        methodDeclaration = methodDeclaration,
                    )
                    else -> null
                }?.let { return@mapNotNullTo it }
            }
            null
        }
    }
}

internal infix fun <A : Annotation> KSAnnotation.sameAs(clazz: KClass<A>): Boolean {
    return shortName.getShortName() == clazz.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() ==
            clazz.qualifiedName
}

internal inline fun <reified A : Annotation> KSAnnotated.getAnnotation(): KSAnnotation? {
    return annotations.find { it sameAs A::class }
}

internal inline fun <reified A : Annotation> KSType.getAnnotation(): KSAnnotation? {
    return annotations.find { it sameAs A::class }
}

internal operator fun KSAnnotation.get(name: String): Any? {
    return arguments.find { (it.name?.asString() ?: "value") == name }?.value
}

internal val KSDeclaration.nameBestEffort: String
    get() = qualifiedName?.asString() ?: (packageName.asString() + simpleName.asString())


internal fun NameModel(declaration: KSClassDeclaration): NameModel {
    with(declaration) {
        return NameModel(
            packageName = packageName.asString(),
            qualifiedName = qualifiedName!!.asString(),
            simpleName = simpleName.asString(),
            typeArguments = emptyList(),
        )
    }
}

internal fun NameModel(type: KSType): NameModel {
    return NameModel(type.declaration as KSClassDeclaration)
        .copy(typeArguments = type.arguments.map { NameModel(it.type!!.resolve()) })
}

internal fun FunctionNameModel(owner: KSType, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = NameModel(owner),
        function = function.simpleName.asString(),
    )
}

internal fun FunctionNameModel(owner: KSClassDeclaration, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = NameModel(owner),
        function = function.simpleName.asString(),
    )
}

internal fun PropertyNameModel(owner: KSType, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = NameModel(owner),
        property = property.simpleName.asString(),
    )
}

internal fun PropertyNameModel(owner: KSClassDeclaration, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = NameModel(owner),
        property = property.simpleName.asString(),
    )
}