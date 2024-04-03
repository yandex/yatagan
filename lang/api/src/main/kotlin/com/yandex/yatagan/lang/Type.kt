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

package com.yandex.yatagan.lang

import com.yandex.yatagan.lang.scope.LexicalScope

/**
 * Models a concrete [TypeDeclaration] usage.
 * Contains additional information, like type arguments.
 *
 * No assumptions about type nullability and other "enhancements" is made.
 *
 * The represented type can be any normal Java type: reference, primitive, array, etc., except the wildcard types.
 *
 * Regarding wildcard types.
 * This model can't represent a wildcard type directly - the system has Kotlin point of view here. Kotlin models
 * type parameter as a *type variable* and a *variance*, and type argument as a *type* and a *projection*.
 * [Type] only provides [typeArguments]; variance, projection and type variables are not exposed as of now.
 *
 */
public interface Type : Comparable<Type>, LexicalScope {
    /**
     * The corresponding type declaration.
     */
    public val declaration: TypeDeclaration

    /**
     * Type arguments. If any of the arguments has non-invariant variance (or a wildcard type) -
     * such is info is not available via the current API.
     */
    public val typeArguments: List<Type>

    /**
     * Checks if the type is the `void` JVM type.
     */
    public val isVoid: Boolean

    /**
     * Checks if the type is either of:
     * 1. Unresolved/missing/error type.
     * 2. Unsubstituted type variable.
     */
    public val isUnresolved: Boolean

    /**
     * Checks if a variable of this type can be assigned a value of [another] type.
     */
    public fun isAssignableFrom(another: Type): Boolean

    /**
     * @return If this is a primitive type, returns its *boxed* counterpart. Returns this type otherwise.
     *
     */
    public fun asBoxed(): Type
}