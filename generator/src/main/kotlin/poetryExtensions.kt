package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.yandex.dagger3.core.CallableNameModel
import com.yandex.dagger3.core.ClassNameModel
import com.yandex.dagger3.core.ConstructorNameModel
import com.yandex.dagger3.core.FunctionNameModel
import com.yandex.dagger3.core.MemberCallableNameModel
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.PropertyNameModel
import com.yandex.dagger3.generator.poetry.ExpressionBuilder
import com.yandex.dagger3.generator.poetry.Names

internal typealias DependencyKind = NodeModel.Dependency.Kind

internal inline fun ClassNameModel.asClassName(
    transformName: (String) -> String,
): ClassName {
    require(typeArguments.isEmpty())
    // FIXME: transform only last name
    return ClassName.get(packageName, transformName(simpleNames.single()))
}

internal fun ClassNameModel.asTypeName(): TypeName {
    val className = when (simpleNames.size) {
        0 -> throw IllegalArgumentException()
        1 -> ClassName.get(packageName, simpleNames.first())
        2 -> ClassName.get(packageName, simpleNames[0], simpleNames[1])
        3 -> ClassName.get(packageName, simpleNames[0], simpleNames[1], simpleNames[2])
        else -> ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }
    return if (typeArguments.isNotEmpty()) {
        ParameterizedTypeName.get(className, *typeArguments.map(ClassNameModel::asTypeName).toTypedArray())
    } else className
}

internal fun NodeModel.Dependency.asTypeName(): TypeName {
    val typeName = node.name.asTypeName()
    return when (kind) {
        DependencyKind.Direct -> typeName
        DependencyKind.Lazy -> ParameterizedTypeName.get(Names.Lazy, typeName)
        DependencyKind.Provider -> ParameterizedTypeName.get(Names.Provider, typeName)
    }
}

internal fun MemberCallableNameModel.functionName() = when (this) {
    is FunctionNameModel -> function
    is PropertyNameModel -> "get${property.capitalize()}"
}

internal fun ExpressionBuilder.call(name: CallableNameModel, arguments: Sequence<Any>) {
    when (name) {
        is ConstructorNameModel -> +"new %T(".formatCode(name.type.asTypeName())
        is MemberCallableNameModel -> +"%T.%N(".formatCode(
            name.ownerName.asTypeName(),
            name.functionName(),
        )
    }
    join(arguments.asSequence()) { arg ->
        +"%L".formatCode(arg)
    }
    +")"
}