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

import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.lang.scope.LexicalScope

/**
 * Models a type declaration. Can represent class/primitive/array/... types.
 *
 * As of now type declaration has 1 : 1 relation to the [type][asType].
 * This allows the declaration members be presented with already resolved class-level generics and eliminated the need
 * for a public API for `asMemberOf()` and the likes. This should be taken into account while comparing two type
 * declarations for equality - they may compare unequal for they have different underlying types.
 */
public interface TypeDeclaration : Annotated, HasPlatformModel, Accessible, Comparable<TypeDeclaration>, LexicalScope {
    /**
     * Declaration kind.
     */
    public val kind: TypeDeclarationKind

    /**
     * Whether the declaration is abstract (abstract class or interface).
     */
    public val isAbstract: Boolean

    /**
     * Qualified/Canonical name of the represented class from the Java point of view.
     *
     * Example: `"com.example.TopLevel.Nested"`, `"int"`, `"void"`, ...
     */
    public val qualifiedName: String

    /**
     * If this declaration is nested, returns enclosing declaration. `null` otherwise.
     */
    public val enclosingType: TypeDeclaration?

    /**
     * Interfaces directly implemented/extended by the declaration.
     */
    public val interfaces: Sequence<Type>

    /**
     * Super-type, if present.
     *
     * NOTE: Never returns `java.lang.Object`/`kotlin.Any`, `null` is returned instead.
     * This is done to counter uniformity issues.
     */
    public val superType: Type?

    /**
     * All declared non-private constructors.
     */
    public val constructors: Sequence<Constructor>

    /**
     * All non-private methods (including static and inherited ones).
     *
     * All returned methods (including inherited or overridden ones) have [owner][Method.owner] defined
     * as `this`.
     *
     * Never includes methods defined in `java.lang.Object`/`kotlin.Any`, as they are of no interest to Yatagan.
     */
    public val methods: Sequence<Method>

    /**
     * All non-private declared fields (including static and inherited).
     */
    public val fields: Sequence<Field>

    /**
     * Nested non-private classes that are declared inside this declaration.
     */
    public val nestedClasses: Sequence<TypeDeclaration>

    /**
     * Kotlin's default companion object declaration, if one exists for the type.
     * If a companion object has non-default name (`"Companion"`), it won't be found here.
     */
    public val defaultCompanionObjectDeclaration: TypeDeclaration?

    /**
     * Returns an underlying [Type].
     */
    public fun asType(): Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnClass> getAnnotation(
        which: BuiltinAnnotation.Target.OnClass<T>
    ): T?

    /**
     * Obtains framework annotations of the given class.
     *
     * @return the list of repeatable annotations or an empty list if no such annotations are present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnClassRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnClassRepeatable<T>
    ): List<T>
}