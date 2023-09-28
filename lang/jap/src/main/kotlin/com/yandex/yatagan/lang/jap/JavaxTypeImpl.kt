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

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

internal class JavaxTypeImpl private constructor(
    val impl: TypeMirror,
) : CtTypeBase() {
    override val nameModel: CtTypeNameModel by lazy { CtTypeNameModel(impl) }

    override val declaration: TypeDeclaration by lazy {
        if (impl.kind == TypeKind.DECLARED) {
            JavaxTypeDeclarationImpl(impl.asDeclaredType())
        } else NoDeclaration(this)
    }

    override val isVoid: Boolean
        get() = impl.kind == TypeKind.VOID

    override fun asBoxed(): Type {
        return Factory(if (impl.kind.isPrimitive) {
            Utils.types.boxedClass(impl.asPrimitiveType()).asType()
        } else impl)
    }

    override val typeArguments: List<Type> by lazy {
        when (impl.kind) {
            TypeKind.DECLARED -> impl.asDeclaredType().typeArguments.map { type ->
                Factory(when(type.kind) {
                    TypeKind.WILDCARD -> type.asWildCardType().let {
                        it.extendsBound ?: it.superBound ?: Utils.objectType.asType()
                    }
                    else -> type
                })
            }
            else -> emptyList()
        }
    }

    override fun isAssignableFrom(another: Type): Boolean {
        return when (another) {
            is JavaxTypeImpl -> Utils.types.isAssignable(another.impl, impl)
            else -> false
        }
    }

    companion object Factory : ObjectCache<TypeMirrorEquivalence, JavaxTypeImpl>() {
        operator fun invoke(impl: TypeMirror): Type {
            return when(impl.kind) {
                TypeKind.ERROR -> CtErrorType(
                    nameModel = InvalidNameModel.Unresolved(hint = impl.toString())
                )
                TypeKind.TYPEVAR -> CtErrorType(
                    nameModel = InvalidNameModel.TypeVariable(
                        typeVar = impl.asTypeVariable().asElement().simpleName.toString(),
                    )
                )
                TypeKind.DECLARED -> {
                    if (impl.asTypeElement().qualifiedName.contentEquals("error.NonExistentClass"))
                        CtErrorType(nameModel = InvalidNameModel.Unresolved(hint = "error.NonExistentClass"))
                    else createCached(TypeMirrorEquivalence(impl)) { JavaxTypeImpl(impl = impl) }
                }
                else -> createCached(TypeMirrorEquivalence(impl)) { JavaxTypeImpl(impl = impl) }
            }
        }
    }
}