package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ClassNameModel
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.ModuleModel
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Scope

data class KspModuleModel(
    val type: KSType,
) : ModuleModel {

    private val declaration = type.declaration as KSClassDeclaration

    init {
        require(canRepresent(declaration))
    }

    private val impl = declaration.getAnnotation<Module>()!!

    @Suppress("UNCHECKED_CAST")
    override val subcomponents: Collection<ComponentModel> by lazy {
        val list = impl["subcomponents"] as? List<KSType> ?: return@lazy emptySet()
        list.mapTo(hashSetOf()) { KspComponentModel(it.declaration as KSClassDeclaration) }
    }

    override val bindings: Collection<Binding> by lazy {
        val list = arrayListOf<Binding>()
        declaration.getDeclaredProperties().mapNotNullTo(list) { propertyDeclaration ->
            val propertyType = propertyDeclaration.asMemberOf(type)
            val target = KspNodeModel(
                type = propertyType,
                forQualifier = propertyDeclaration,
            )
            propertyDeclaration.annotations.forEach { ann ->
                when {
                    ann hasType Provides::class -> ProvisionBinding(
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
                    ann hasType Binds::class -> AliasBinding(
                        target = target,
                        source = KspNodeModel(
                            type = methodDeclaration.parameters.single().type.resolve(),
                            forQualifier = methodDeclaration.parameters.single(),
                        ),
                        scope = KspAnnotationDescriptor.describeIfAny<Scope>(methodDeclaration),
                    )
                    ann hasType Provides::class -> ProvisionBinding(
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

    override val name: ClassNameModel by lazy { ClassNameModel(type) }

    companion object {
        fun canRepresent(declaration: KSClassDeclaration): Boolean {
            return declaration.isAnnotationPresent(Module::class)
        }
    }
}