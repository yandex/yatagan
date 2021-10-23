package com.yandex.daggerlite.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.core.AliasBinding
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.generator.ClassNameModel

class KspModuleModel(
    private val type: KSType,
) : ModuleModel() {

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

    override val bindings: Collection<BaseBinding> by lazy {
        val list = arrayListOf<BaseBinding>()
        val mayRequireInstance = !declaration.isAbstract() && !declaration.isObject

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
                        requiredModuleInstance = this.takeIf { mayRequireInstance && !propertyDeclaration.isStatic }
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
                        // TODO: forbid scope on alias bindings
                    )
                    ann hasType Provides::class -> ProvisionBinding(
                        target = target,
                        ownerType = type,
                        method = method,
                        methodDeclaration = methodDeclaration,
                        requiredModuleInstance = this.takeIf { mayRequireInstance && !methodDeclaration.isStatic }
                    )
                    else -> null
                }?.let { return@mapNotNullTo it }
            }
            null
        }
    }

    override val isInstanceRequired: Boolean by lazy {
        !declaration.isAbstract() && bindings.any { it is ProvisionBinding && it.requiredModuleInstance != null }
    }

    override val id: ClassNameModel by lazy { ClassNameModel(type) }

    override fun toString() = "Module[$id]"

    companion object {
        fun canRepresent(declaration: KSClassDeclaration): Boolean {
            return declaration.isAnnotationPresent<Module>()
        }
    }
}
