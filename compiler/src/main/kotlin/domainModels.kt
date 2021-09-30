package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.*
import com.yandex.dagger3.core.*
import dagger.Binds
import dagger.Component
import dagger.Provides
import javax.inject.Inject
import javax.inject.Qualifier
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

    override val entryPoints: Set<Pair<String, NodeModel>> by lazy {
        buildSet {
            for (function in node.getAllFunctions().filter { it.isAbstract }) {
                add(
                    function.simpleName.asString() to KspNodeModel(
                        reference = function.returnType ?: continue,
                        forQualifier = function,
                    )
                )
            }
            for (prop in node.getAllProperties().filter { it.isAbstract() && !it.isMutable }) {
                add(
                    "get${prop.simpleName.asString().capitalize()}"  to KspNodeModel(
                        reference = prop.type, forQualifier = prop
                    )
                )
            }
        }
    }
}

data class KspNodeQualifier(val tag: String) : NodeQualifier {
    companion object {
        fun describe(annotation: KSAnnotation): KspNodeQualifier {
            val descriptor = buildString {
                append(annotation.annotationType.resolve().declaration.nameBestEffort)
                append('(')
                annotation.arguments.joinTo(this, separator = ";") {
                    "${it.name?.asString()}=${it.value}"
                }
                append(')')
            }
            return KspNodeQualifier(descriptor)
        }
    }
}

data class KspNodeModel(
    val type: KSType,
    override val qualifier: KspNodeQualifier?,
) : NodeModel {
    constructor(
        reference: KSTypeReference,
        forQualifier: KSAnnotated,
    ) : this(
        type = reference.resolve(),
        qualifier = forQualifier.annotations.find {
            it.annotationType.resolve().declaration.getAnnotation<Qualifier>() != null
        }?.let { KspNodeQualifier.describe(it) }
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
                method = injectConstructor,
            )
        }
    }

    override val name: NameModel by lazy {
        type.declaration.nameModel
    }
}

sealed class BaseBinding(
    override val target: NodeModel,
    override val dependencies: Set<NodeModel>,
) : Binding

class AliasBinding(
    source: NodeModel,
    target: NodeModel,
) : BaseBinding(target = target, dependencies = setOf(source))

class ProvisionBinding(
    target: NodeModel,
    val method: KSDeclaration,
) : BaseBinding(
    target = target, dependencies = when (method) {
        is KSFunctionDeclaration -> method.parameters.map {
            KspNodeModel(
                reference = it.type,
                forQualifier = it,
            )
        }.toSet()
        else -> emptySet()
    }
)

data class KspModuleModel(
    val node: KSClassDeclaration,
) : ModuleModel {
    override val bindings: Collection<Binding> by lazy {
        node.getDeclaredFunctions().mapNotNullTo(arrayListOf()) { method ->
            val target = KspNodeModel(
                reference = method.returnType ?: return@mapNotNullTo null,
                forQualifier = method,
            )
            method.annotations.forEach { ann ->
                when {
                    ann sameAs Binds::class -> AliasBinding(
                        target = target,
                        source = KspNodeModel(
                            reference = method.parameters.single().type,
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
    get() = qualifiedName?.let {
        NameModel(
            packageName = it.getQualifier(),
            name = it.getShortName(),
        )
    } ?: NameModel(
        packageName = packageName.asString(),
        name = simpleName.asString(),
    )