package com.yandex.dagger3.compiler

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ClassNameModel
import com.yandex.dagger3.core.NodeModel
import javax.inject.Inject
import javax.inject.Qualifier

data class KspNodeModel (
    val type: KSType,
    override val qualifier: KspAnnotationDescriptor?,
) : NodeModel {
    constructor(
        type: KSType,
        forQualifier: KSAnnotated,
    ) : this(
        type = type.makeNotNullable(),  // TODO(jeffset): nullability is forbidden anyway
        qualifier = KspAnnotationDescriptor.describeIfAny<Qualifier>(forQualifier)
    )

    override val defaultBinding: Binding? by lazy {
        if (qualifier != null) null
        else when (val declaration = type.declaration) {
            is KSClassDeclaration -> declaration.getConstructors().find {
                it.isAnnotationPresent<Inject>()
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

    override val name: ClassNameModel by lazy {
        ClassNameModel(type)
    }

    override fun toString() = "${qualifier ?: ""} $name"
}