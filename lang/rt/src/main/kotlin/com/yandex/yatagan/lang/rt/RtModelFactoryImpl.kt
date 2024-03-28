/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.LangModelFactoryFallback
import com.yandex.yatagan.lang.scope.LexicalScope

internal class RtModelFactoryImpl(
    lexicalScope: LexicalScope,
    private val classLoader: ClassLoader,
) : LangModelFactoryFallback(), LexicalScope by lexicalScope {
    private val listClass = classLoader.loadClass("java.util.List")
    private val setClass = classLoader.loadClass("java.util.Set")
    private val mapClass = classLoader.loadClass("java.util.Map")
    private val collectionClass = classLoader.loadClass("java.util.Collection")
    private val providerClass = classLoader.loadClass("javax.inject.Provider")

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        if (parameter !is RtTypeImpl) {
            return super.getParameterizedType(type, parameter, isCovariant)
        }
        val clazz = when (type) {
            LangModelFactory.ParameterizedType.List -> listClass
            LangModelFactory.ParameterizedType.Set -> setClass
            LangModelFactory.ParameterizedType.Collection -> collectionClass
            LangModelFactory.ParameterizedType.Provider -> providerClass
        }
        val arg = if (isCovariant) WildcardTypeImpl(upperBound = parameter.impl) else parameter.impl
        return RtTypeImpl(ParameterizedTypeImpl(arg, raw = clazz))
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        if (keyType !is RtTypeImpl || valueType !is RtTypeImpl) {
            return super.getMapType(keyType, valueType, isCovariant)
        }
        val valueArg = if (isCovariant) WildcardTypeImpl(upperBound = valueType.impl) else valueType.impl
        return RtTypeImpl(ParameterizedTypeImpl(keyType.impl, valueArg, raw = mapClass))
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclaration? {
        val qualifiedName = buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            append(simpleName)
            for (name in simpleNames) {
                append('$').append(name)
            }
        }
        return try {
            RtTypeImpl(classLoader.loadClass(qualifiedName)).declaration
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    override val isInRuntimeEnvironment: Boolean
        get() = true

    private class WildcardTypeImpl(
        private val upperBound: ReflectType,
    ) : ReflectWildcardType {
        private val upperBounds = arrayOf(upperBound)
        override fun getUpperBounds() = upperBounds
        override fun getLowerBounds() = emptyArray<ReflectType>()
        override fun toString() = "? extends $upperBound"
    }

    private class ParameterizedTypeImpl(
        private vararg val arguments: ReflectType,
        private val raw: ReflectType,
    ) : ReflectParameterizedType {
        override fun getActualTypeArguments() = arguments
        override fun getRawType() = raw
        override fun getOwnerType() = null
        override fun toString() = buildString {
            append(raw)
            arguments.joinTo(this, prefix = "<", postfix = ">")
        }
    }
}
