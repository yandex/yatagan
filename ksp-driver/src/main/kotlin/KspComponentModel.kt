package com.yandex.daggerlite.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.core.ComponentDependencyFactoryInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.ModuleInstanceFactoryInput
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.generator.ClassNameModel
import com.yandex.daggerlite.generator.MemberCallableNameModel
import javax.inject.Scope

class KspComponentModel(
    val componentDeclaration: KSClassDeclaration,
) : ComponentModel() {
    init {
        require(canRepresent(componentDeclaration))
    }

    override val id: ClassNameModel = ClassNameModel(componentDeclaration)

    private val impl: KSAnnotation = componentDeclaration.getAnnotation<Component>()!!
    override val isRoot: Boolean = impl["isRoot"] as Boolean

    override val factory: ComponentFactoryModel? by lazy {
        val factoryDeclaration = componentDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .find { it.isAnnotationPresent<Component.Factory>() }
            ?: return@lazy null

        val factoryMethod = factoryDeclaration.getDeclaredFunctions().find { it.simpleName.asString() == "create" }
            ?: return@lazy null

        object : ComponentFactoryModel() {
            override val createdComponent: ComponentModel
                get() = this@KspComponentModel
            override val id = ClassNameModel(factoryDeclaration)
            override val inputs = factoryMethod.parameters.map {
                val type = it.type.resolve()
                val declaration = type.declaration as KSClassDeclaration
                val name = it.name!!.asString()
                when {
                    canRepresent(declaration) ->
                        ComponentDependencyFactoryInput(KspComponentModel(declaration), name)
                    KspModuleModel.canRepresent(declaration) ->
                        ModuleInstanceFactoryInput(KspModuleModel(type), name)
                    it.isAnnotationPresent<BindsInstance>() ->
                        InstanceBinding(KspNodeModel(type, forQualifier = it), name)
                    else -> throw IllegalStateException("invalid factory method parameter")
                }
            }
        }
    }

    override val scope: ProvisionBinding.Scope? by lazy {
        KspAnnotationDescriptor.describeIfAny<Scope>(componentDeclaration)
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

    override val entryPoints: Set<EntryPoint> by lazy {
        buildSet {
            for (function in componentDeclaration.getAllFunctions().filter { it.isAbstract }) {
                this += KspEntryPoint(
                    dependency = resolveNodeDependency(
                        type = function.returnType?.resolve() ?: continue,
                        forQualifier = function,
                    ),
                    id = FunctionNameModel(componentDeclaration, function),
                )
            }
            for (prop in componentDeclaration.getAllProperties().filter { it.isAbstract() && !it.isMutable }) {
                this += KspEntryPoint(
                    dependency = resolveNodeDependency(type = prop.type.resolve(), forQualifier = prop),
                    id = PropertyNameModel(componentDeclaration, prop),
                )
            }
        }
    }

    private data class KspEntryPoint(
        override val id: MemberCallableNameModel,
        override val dependency: Dependency,
    ) : EntryPoint

    companion object {
        fun canRepresent(declaration: KSClassDeclaration): Boolean {
            return declaration.isAnnotationPresent<Component>()
        }
    }
}
