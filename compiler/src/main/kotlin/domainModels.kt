package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ComponentModel
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
    val node: KSClassDeclaration,
) : ComponentModel {
    override val name: NameModel = node.nameModel

    private val impl = requireNotNull(node.getAnnotation<Component>()) {
        "declaration $node can't be represented by ComponentModel"
    }

    @Suppress("UNCHECKED_CAST")
    override val modules: Set<KspModuleModel> by lazy {
        val list = impl["modules"] as? List<KSType> ?: return@lazy emptySet()
        list.mapTo(hashSetOf()) { KspModuleModel(it.declaration as KSClassDeclaration) }
    }

    @Suppress("UNCHECKED_CAST")
    override val dependencies: Set<KspComponentModel> by lazy {
        val list = impl["dependencies"] as? List<KSType> ?: return@lazy emptySet()
        list.mapTo(hashSetOf()) { KspComponentModel(it.declaration as KSClassDeclaration) }
    }

    override val entryPoints: Set<EntryPointModel> by lazy {
        buildSet {
            for (function in node.getAllFunctions().filter { it.isAbstract }) {
                this += EntryPointModel(
                    dep = NodeDependency.resolveFromType(
                        type = function.returnType?.resolve() ?: continue,
                        forQualifier = function,
                    ),
                    getterName = function.simpleName.asString(),
                )
            }
            for (prop in node.getAllProperties().filter { it.isAbstract() && !it.isMutable }) {
                this += EntryPointModel(
                    dep = NodeDependency.resolveFromType(type = prop.type.resolve(), forQualifier = prop),
                    getterName = "get${prop.simpleName.asString().capitalize()}",
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
                append('(')
                annotation.arguments.joinTo(this, separator = ";") {
                    "${it.name?.asString()}=${it.value}"
                }
                append(')')
            }
            return KspAnnotationDescriptor(descriptor)
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
        qualifier = forQualifier.annotations.find {
            it.annotationType.resolve().declaration.getAnnotation<Qualifier>() != null
        }?.let(KspAnnotationDescriptor::describe)
    )

    override val scope: NodeScope? by lazy {
        type.declaration.annotations.find {
            it.annotationType.resolve().declaration.getAnnotation<Scope>() != null
        }?.let(KspAnnotationDescriptor::describe)
    }

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
                method = injectConstructor,
            )
        }
    }

    override val name: NameModel by lazy {
        type.declaration.nameModel
    }

    override fun toString() = "${qualifier ?: ""} $name"
}

fun ProvisionBinding(
    target: NodeModel,
    method: KSDeclaration,
) = ProvisionBinding(
    target = target,
    provider = when (method) {
        is KSFunctionDeclaration -> method.nameModel
        is KSPropertyDeclaration -> method.nameModel
        else -> throw IllegalArgumentException("invalid declaration for provision")
    },
    params = when (method) {
        is KSFunctionDeclaration -> method.parameters.map { param ->
            NodeDependency.resolveFromType(type = param.type.resolve(), forQualifier = param)
        }.toSet()
        is KSPropertyDeclaration -> emptySet()
        else -> throw IllegalArgumentException("invalid declaration for provision")
    }
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
    val node: KSClassDeclaration,
) : ModuleModel {
    override val bindings: Collection<Binding> by lazy {
        val list = arrayListOf<Binding>()
        node.getDeclaredProperties().mapNotNullTo(list) { prop ->
            val target = KspNodeModel(
                type = prop.type.resolve(),
                forQualifier = prop,
            )
            prop.annotations.forEach { ann ->
                when {
                    ann sameAs Provides::class -> ProvisionBinding(
                        target = target,
                        method = prop,
                    )
                    else -> null
                }?.let { return@mapNotNullTo it }
            }
            null
        }
        node.getDeclaredFunctions().mapNotNullTo(list) { method ->
            val target = KspNodeModel(
                type = method.returnType?.resolve() ?: return@mapNotNullTo null,
                forQualifier = method,
            )
            method.annotations.forEach { ann ->
                when {
                    ann sameAs Binds::class -> AliasBinding(
                        target = target,
                        source = KspNodeModel(
                            type = method.parameters.single().type.resolve(),
                            forQualifier = method.parameters.single(),
                        ),
                    )
                    ann sameAs Provides::class -> ProvisionBinding(
                        target = target,
                        method = method,
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


internal val KSDeclaration.nameModel: NameModel
    get() = NameModel(
        packageName = packageName.asString(),
        qualifiedName = qualifiedName!!.asString(),
        simpleName = simpleName.asString(),
    )

internal val KSFunctionDeclaration.nameModel: FunctionNameModel
    get() = FunctionNameModel(
        ownerName = (parentDeclaration as KSClassDeclaration).nameModel,
        name = simpleName.asString(),
    )

internal val KSPropertyDeclaration.nameModel: PropertyNameModel
    get() = PropertyNameModel(
        ownerName = (parentDeclaration as KSClassDeclaration).nameModel,
        name = simpleName.asString(),
    )