package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ModuleModel
import dagger.Binds
import dagger.Provides
import javax.inject.Scope

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