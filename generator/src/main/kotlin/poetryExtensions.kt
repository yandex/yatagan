package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.WildcardTypeName
import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.generator.lang.ParameterizedNameModel
import com.yandex.daggerlite.generator.lang.WildcardNameModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder

internal inline fun CtTypeNameModel.asClassName(
    transformName: (String) -> String,
): ClassName {
    return when (this) {
        is ClassNameModel -> when (simpleNames.size) {
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
        else -> throw IllegalArgumentException("Unexpected type: $this")
    }
}

internal fun ClassBackedModel.typeName(): TypeName {
    return name.asTypeName()
}

internal fun TypeLangModel.typeName(): TypeName {
    return name.asTypeName()
}

private fun ClassNameModel.asTypeName(): ClassName {
    return when (simpleNames.size) {
        0 -> throw IllegalArgumentException()
        1 -> ClassName.get(packageName, simpleNames.first())
        else -> ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }
}

private fun CtTypeNameModel.asTypeName(): TypeName {
    return when (this) {
        is ClassNameModel -> asTypeName()
        is ParameterizedNameModel -> ParameterizedTypeName.get(
            raw.asTypeName(), *typeArguments.map { it.asTypeName() }.toTypedArray())
        is WildcardNameModel ->
            upperBound?.let { WildcardTypeName.subtypeOf(it.asTypeName()) }
                ?: lowerBound?.let { WildcardTypeName.supertypeOf(it.asTypeName()) }
                ?: WildcardTypeName.subtypeOf(TypeName.OBJECT)
    }
}

internal inline fun <A> ExpressionBuilder.generateCall(
    callable: CallableLangModel,
    arguments: Iterable<A>,
    instance: String?,
    crossinline argumentBuilder: ExpressionBuilder.(A) -> Unit,
) {
    when(callable) {
        is ConstructorLangModel -> +"new %T(".formatCode(callable.constructee.asType().typeName())
        is FunctionLangModel -> {
            if (instance != null) {
                +"$instance.%N(".formatCode(callable.name)
            } else {
                val companion = callable.companionObjectName
                val ownerObject = when {
                    callable.owner.isKotlinObject -> ".INSTANCE"
                    companion != null && !callable.isStatic -> ".$companion"
                    else -> ""
                }
                +"%T%L.%L(".formatCode(callable.ownerName.asTypeName(), ownerObject, callable.name)
            }
        }
    }
    join(arguments) { arg ->
        argumentBuilder(arg)
    }
    +")"
}
