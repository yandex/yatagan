package com.yandex.daggerlite.compiler

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.generator.ClassNameModel
import javax.inject.Inject

class KspNodeModel(
    private val type: KSType,
    override val qualifier: KspAnnotationDescriptor?,
) : NodeModel() {
    constructor(
        type: KSType,
        forQualifier: KSAnnotated,
    ) : this(
        type = type.makeNotNullable(),  // TODO(jeffset): nullability is forbidden anyway
        qualifier = KspAnnotationDescriptor.describeIfAny<javax.inject.Qualifier>(forQualifier)
    )

    override fun implicitBinding(): Binding? {
        if (qualifier != null)
            return null

        return when (val declaration = type.declaration) {
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
                requiredModuleInstance = null,
            )
        }
    }

    override val id: ClassNameModel by lazy {
        ClassNameModel(type)
    }
}