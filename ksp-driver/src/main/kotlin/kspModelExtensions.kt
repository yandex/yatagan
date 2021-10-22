package com.yandex.daggerlite.compiler

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.daggerlite.Lazy
import com.yandex.daggerlite.core.ClassNameModel
import com.yandex.daggerlite.core.ConstructorNameModel
import com.yandex.daggerlite.core.FunctionNameModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind
import com.yandex.daggerlite.core.PropertyNameModel
import com.yandex.daggerlite.core.ProvisionBinding
import javax.inject.Provider
import javax.inject.Scope

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    methodDeclaration: KSFunctionDeclaration,
    method: KSFunction = methodDeclaration.asMemberOf(ownerType),
    forScope: KSAnnotated = methodDeclaration,
    requiredModuleInstance: ModuleModel?,
) = ProvisionBinding(
    target = target,
    provider = if (methodDeclaration.isConstructor()) {
        ConstructorNameModel(ClassNameModel(ownerType))
    } else FunctionNameModel(ownerType, methodDeclaration),
    params = method.parameterTypes
        .zip(methodDeclaration.parameters)
        .map { (paramType, param) ->
            resolveNodeDependency(type = paramType!!, forQualifier = param)
        },
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(forScope),
    requiredModuleInstance = requiredModuleInstance,
)

fun ProvisionBinding(
    target: NodeModel,
    ownerType: KSType,
    propertyDeclaration: KSPropertyDeclaration,
    requiredModuleInstance: ModuleModel?,
) = ProvisionBinding(
    target = target,
    provider = PropertyNameModel(ownerType, propertyDeclaration),
    params = emptyList(),
    scope = KspAnnotationDescriptor.describeIfAny<Scope>(propertyDeclaration),
    requiredModuleInstance = requiredModuleInstance,
)

internal fun resolveNodeDependency(
    type: KSType,
    forQualifier: KSAnnotated,
): NodeModel.Dependency {
    val kind = when (type.declaration.qualifiedName?.asString()) {
        Lazy::class.qualifiedName -> Kind.Lazy
        Provider::class.qualifiedName -> Kind.Provider
        else -> Kind.Direct
    }
    return NodeModel.Dependency(
        node = KspNodeModel(
            type = when (kind) {
                Kind.Direct -> type
                else -> type.arguments[0].type!!.resolve()
            },
            forQualifier = forQualifier,
        ),
        kind = kind,
    )
}

internal fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val packageName = declaration.packageName.asString()
    // MAYBE: use KSName api instead of string manipulation.
    val names = requireNotNull(declaration.qualifiedName)
        .asString().substring(startIndex = packageName.length + 1)
        .split('.')
    return ClassNameModel(
        packageName = packageName,
        simpleNames = names,
        typeArguments = emptyList(),
    )
}

internal fun ClassNameModel(type: KSType): ClassNameModel {
    return ClassNameModel(type.declaration as KSClassDeclaration)
        .copy(typeArguments = type.arguments.map { ClassNameModel(it.type!!.resolve()) })
}

internal fun FunctionNameModel(owner: KSType, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = ClassNameModel(owner),
        function = function.simpleName.asString(),
        isOwnerKotlinObject = owner.declaration.isObject && !function.isStatic,
    )
}

internal fun FunctionNameModel(owner: KSClassDeclaration, function: KSFunctionDeclaration): FunctionNameModel {
    return FunctionNameModel(
        ownerName = ClassNameModel(owner),
        function = function.simpleName.asString(),
        isOwnerKotlinObject = owner.isObject && !function.isStatic,
    )
}

internal fun PropertyNameModel(owner: KSType, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = ClassNameModel(owner),
        property = property.simpleName.asString(),
        isOwnerKotlinObject = owner.declaration.isObject && !property.isStatic,
    )
}

internal fun PropertyNameModel(owner: KSClassDeclaration, property: KSPropertyDeclaration): PropertyNameModel {
    return PropertyNameModel(
        ownerName = ClassNameModel(owner),
        property = property.simpleName.asString(),
        isOwnerKotlinObject = owner.isObject && !property.isStatic,
    )
}
