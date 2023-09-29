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

package com.yandex.yatagan.lang.common

import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind

/**
 * A [TypeDeclaration] implementation, that is convenient to return when no declaration makes sense at all,
 * e.g. for primitive types, `void` type, array type, etc.
 */
class NoDeclaration(
    private val type: Type,
) : TypeDeclarationBase() {
    override val isAbstract get() = false
    override val isEffectivelyPublic get() = false

    override val annotations get() = emptySequence<Nothing>()
    override val interfaces get() = emptySequence<Nothing>()
    override val constructors get() = emptySequence<Nothing>()
    override val methods get() = emptySequence<Nothing>()
    override val fields get() = emptySequence<Nothing>()
    override val nestedClasses get() = emptySequence<Nothing>()

    override val superType: Nothing? get() = null
    override val defaultCompanionObjectDeclaration: Nothing? get() = null
    override val enclosingType: Nothing? get() = null
    override val platformModel: Nothing? get() = null

    override val kind: TypeDeclarationKind
        get() = TypeDeclarationKind.None

    override val qualifiedName: String
        get() = type.toString()

    override fun <T : BuiltinAnnotation.OnClass> getAnnotation(
        which: BuiltinAnnotation.Target.OnClass<T>
    ): Nothing? = null

    override fun <T : BuiltinAnnotation.OnClassRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnClassRepeatable<T>
    ): List<T> = emptyList()

    override fun asType(): Type = type

    override fun hashCode(): Int = type.hashCode()
    override fun equals(other: Any?): Boolean {
        return this === other || (other is NoDeclaration && type == other.type)
    }
}