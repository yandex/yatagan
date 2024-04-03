package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.scope.LexicalScope

abstract class LangModelFactoryFallback : LangModelFactory, LexicalScope {
    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        val covariance = if (isCovariant) "? extends " else ""
        return createNoType(name = "Map<$keyType, ${covariance}$valueType>")
    }

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        val covariance = if (isCovariant) "? extends " else ""
        return createNoType(name = "${type.name}<${covariance}$type>")
    }

    override fun createNoType(name: String): Type {
        return ErrorType(
            nameHint = name,
            // This is a synthetic "no"-type, it's assumed to be resolved: no need to report it in validation.
            isUnresolved = false,
        )
    }
}