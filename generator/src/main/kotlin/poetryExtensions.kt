package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
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
        else -> ClassName.get(
            packageName, simpleNames.first(), *simpleNames
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
    if (packageName == "kotlin") {
        when (simpleNames.first()) {
            "String" -> return Names.String
            // MAYBE: it's not always necessary to use wrapper types?
            "Int" -> return Names.Integer
            "Long" -> return Names.Long
            "Boolean" -> return Names.Boolean
            "Char" -> return Names.Character
            "Byte" -> return Names.Byte
        }
    }
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
    val typeName = node.typeName()
    return when (kind) {
        DependencyKind.Direct -> typeName
        DependencyKind.Lazy -> ParameterizedTypeName.get(Names.Lazy, typeName)
        DependencyKind.Provider -> ParameterizedTypeName.get(Names.Provider, typeName)
    }
}

internal inline fun <A> ExpressionBuilder.generateCall(
    function: FunctionLangModel,
    arguments: Iterable<A>,
    instance: String?,
    crossinline argumentBuilder: ExpressionBuilder.(A) -> Unit,
) {
    when {
        function.isConstructor -> +"new %T(".formatCode(function.ownerName.asTypeName())
        else -> {
            if (instance != null) {
                +"$instance.%N(".formatCode(function.name)
            } else {
                val ownerObject = when {
                    function.owner.isKotlinObject -> ".INSTANCE"
                    function.isFromCompanionObject && !function.isStatic -> ".Companion"
                    else -> ""
                }
                +"${function.ownerName.asTypeName()}$ownerObject.${function.name}("
            }
        }
    }
    join(arguments) { arg ->
        argumentBuilder(arg)
    }
    +")"
}
