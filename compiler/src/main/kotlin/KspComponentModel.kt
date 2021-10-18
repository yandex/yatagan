package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.ClassNameModel
import com.yandex.dagger3.core.ComponentDependencyFactoryInput
import com.yandex.dagger3.core.ComponentFactoryModel
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.InstanceBinding
import com.yandex.dagger3.core.ModuleInstanceFactoryInput
import com.yandex.dagger3.core.ProvisionBinding
import dagger.BindsInstance
import dagger.Component
import javax.inject.Scope

data class KspComponentModel(
    val componentDeclaration: KSClassDeclaration,
) : ComponentModel() {
    init {
        require(canRepresent(componentDeclaration))
    }

    override val name: ClassNameModel = ClassNameModel(componentDeclaration)

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
            override val target: ComponentModel
                get() = this@KspComponentModel
            override val name = ClassNameModel(factoryDeclaration)
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

    override val entryPoints: Set<ComponentModel.EntryPoint> by lazy {
        buildSet {
            for (function in componentDeclaration.getAllFunctions().filter { it.isAbstract }) {
                this += ComponentModel.EntryPoint(
                    dep = resolveNodeDependency(
                        type = function.returnType?.resolve() ?: continue,
                        forQualifier = function,
                    ),
                    getter = FunctionNameModel(componentDeclaration, function),
                )
            }
            for (prop in componentDeclaration.getAllProperties().filter { it.isAbstract() && !it.isMutable }) {
                this += ComponentModel.EntryPoint(
                    dep = resolveNodeDependency(type = prop.type.resolve(), forQualifier = prop),
                    getter = PropertyNameModel(componentDeclaration, prop),
                )
            }
        }
    }

    companion object {
        fun canRepresent(declaration: KSClassDeclaration): Boolean {
            return declaration.isAnnotationPresent<Component>()
        }
    }
}