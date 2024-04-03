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

package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.compiled.CtLangModelFactoryBase
import com.yandex.yatagan.lang.scope.LexicalScope
import javax.lang.model.element.TypeElement

internal class JavaxModelFactoryImpl(
    context: LexicalScope,
) : CtLangModelFactoryBase(), LexicalScope by context {
    private val listElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.List")
    }
    private val setElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Set")
    }
    private val collectionElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Collection")
    }
    private val mapElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Map")
    }
    private val providerElement: TypeElement by lazy {
        Utils.elements.getTypeElement("javax.inject.Provider")
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        if (keyType !is JavaxTypeImpl || valueType !is JavaxTypeImpl) {
            return super.getMapType(keyType, valueType, isCovariant)
        }
        with(Utils.types) {
            val valueArgType =
                if (isCovariant) getWildcardType(/*extends*/ valueType.impl, /*super*/ null)
                else valueType.impl
            return JavaxTypeImpl(getDeclaredType(mapElement, keyType.impl, valueArgType))
        }
    }

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        if (parameter !is JavaxTypeImpl) {
            return super.getParameterizedType(type, parameter, isCovariant)
        }
        val element = when(type) {
            LangModelFactory.ParameterizedType.List -> listElement
            LangModelFactory.ParameterizedType.Set -> setElement
            LangModelFactory.ParameterizedType.Collection -> collectionElement
            LangModelFactory.ParameterizedType.Provider -> providerElement
        }
        with(Utils.types) {
            val typeImpl = parameter.impl
            val argType = if (isCovariant) getWildcardType(/*extends*/ typeImpl,/*super*/ null) else typeImpl
            return JavaxTypeImpl(getDeclaredType(element, argType))
        }
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclaration? {
        val name = buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            append(simpleName)
            for (name in simpleNames) append('.').append(name)
        }
        val element = Utils.elements.getTypeElement(name) ?: return null
        return JavaxTypeDeclarationImpl(element.asType().asDeclaredType())
    }

    override val isInRuntimeEnvironment: Boolean
        get() = false
}
