package com.yandex.dagger3.compiler

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.dagger3.core.ConstructorNameModel
import com.yandex.dagger3.core.FunctionNameModel
import com.yandex.dagger3.core.NameModel
import com.yandex.dagger3.core.NodeDependency
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.PropertyNameModel
import com.yandex.dagger3.core.ProvisionBinding
import dagger.Lazy
import javax.inject.Provider
import javax.inject.Scope

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    methodDeclaration: KSFunctionDeclaration,
    method: KSFunction = methodDeclaration.asMemberOf(ownerType),
    forScope: KSAnnotated = methodDeclaration,
) = ProvisionBinding(
    target = target,
    provider = if (methodDeclaration.isConstructor()) {
        ConstructorNameModel(NameModel(ownerType))
    } else FunctionNameModel(ownerType, methodDeclaration),
    params = method.parameterTypes
        .zip(methodDeclaration.parameters)
        .map { (paramType, param) ->
            NodeDependency.resolveFromType(type = paramType!!, forQualifier = param)
        },
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(forScope),
)

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    propertyDeclaration: KSPropertyDeclaration,
) = ProvisionBinding(
    target = target,
    provider = PropertyNameModel(ownerType, propertyDeclaration),
    params = emptyList(),
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(propertyDeclaration),
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

internal fun NameModel(declaration: KSClassDeclaration): NameModel {
    with(declaration) {
        return NameModel(
            packageName = packageName.asString(),
            qualifiedName = qualifiedName!!.asString(),
            simpleName = simpleName.asString(),
            typeArguments = emptyList(),
        )
    }
}

internal fun NameModel(type: KSType): NameModel {
    return NameModel(type.declaration as KSClassDeclaration)
        .copy(typeArguments = type.arguments.map { NameModel(it.type!!.resolve()) })
}

internal fun FunctionNameModel(owner: KSType, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = NameModel(owner),
        function = function.simpleName.asString(),
    )
}

internal fun FunctionNameModel(owner: KSClassDeclaration, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = NameModel(owner),
        function = function.simpleName.asString(),
    )
}

internal fun PropertyNameModel(owner: KSType, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = NameModel(owner),
        property = property.simpleName.asString(),
    )
}

internal fun PropertyNameModel(owner: KSClassDeclaration, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = NameModel(owner),
        property = property.simpleName.asString(),
    )
}