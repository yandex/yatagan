package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.ClassNameModel
import com.yandex.dagger3.core.ComponentDependency
import com.yandex.dagger3.core.ComponentFactoryModel
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.EntryPointModel
import com.yandex.dagger3.core.FactoryInput
import com.yandex.dagger3.core.InstanceBinding
import com.yandex.dagger3.core.ModuleInstance
import com.yandex.dagger3.core.NodeDependency
import dagger.BindsInstance
import dagger.Component

data class KspComponentModel(
    val componentDeclaration: KSClassDeclaration,
) : ComponentModel {
    init {
        require(canRepresent(componentDeclaration))
    }

    override val name: ClassNameModel = NameModel(componentDeclaration)

    private val impl: KSAnnotation = componentDeclaration.getAnnotation<Component>()!!
    override val isRoot: Boolean = impl["isRoot"] as Boolean

    override val factory: ComponentFactoryModel? by lazy {
        val factoryDeclaration = componentDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .find { it.isAnnotationPresent(Component.Factory::class) }
            ?: return@lazy null

        val factoryMethod = factoryDeclaration.getDeclaredFunctions().find { it.simpleName.asString() == "create" }
            ?: return@lazy null

        object : ComponentFactoryModel {
            override val name = NameModel(factoryDeclaration)
            override val inputs: Collection<FactoryInput> = factoryMethod.parameters.map {
                val type = it.type.resolve()
                val declaration = type.declaration as KSClassDeclaration
                val name = it.name!!.asString()
                when {
                    canRepresent(declaration) ->
                        ComponentDependency(KspComponentModel(declaration), name)
                    KspModuleModel.canRepresent(declaration) ->
                        ModuleInstance(KspModuleModel(type), name)
                    it.isAnnotationPresent(BindsInstance::class) ->
                        InstanceBinding(KspNodeModel(type, forQualifier = it), name)
                    else -> throw IllegalStateException("invalid factory method parameter")
                }
            }
        }
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

    companion object {
        fun canRepresent(declaration: KSClassDeclaration): Boolean {
            return declaration.isAnnotationPresent(Component::class)
        }
    }
}