package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.yandex.daggerlite.core.CallableNameModel
import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.ClassNameModel
import com.yandex.daggerlite.core.ConstructorNameModel
import com.yandex.daggerlite.core.FunctionNameModel
import com.yandex.daggerlite.core.MemberCallableNameModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.PropertyNameModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.Names

internal typealias DependencyKind = NodeModel.Dependency.Kind

internal inline fun ClassNameModel.asClassName(
    transformName: (String) -> String,
): ClassName {
    require(typeArguments.isEmpty()) {
        "Can't transform type name with type arguments"
    }
    return when (simpleNames.size) {
        1 -> ClassName.get(packageName, transformName(simpleNames.first()))
        else -> ClassName.get(packageName, simpleNames.first(), *simpleNames
            .mapIndexed { index, name ->
                if (index == simpleNames.lastIndex) {
                    transformName(name)
                } else name
            }.drop(1).toTypedArray()
        )
    }
}

internal fun ClassBackedModel.typeName(): TypeName {
    return name.asTypeName()
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