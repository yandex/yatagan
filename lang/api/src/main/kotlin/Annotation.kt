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

/**
 * Models annotation instance of any class.
 */
public interface Annotation : HasPlatformModel {
    /**
     * Annotation class declaration.
     */
    public val annotationClass: AnnotationDeclaration

    /**
     * Get the value of an [attribute], returning default value if no explicit value was specified.
     *
     * Please note, that using this method for obtaining attributes values is only justified for user-defined
     * annotations, whose definition is unknown to the framework.
     * For framework annotations use corresponding concrete annotation models, that may optimize the way they obtain
     *  the attributes, like e.g. [BuiltinAnnotation.Component], [BuiltinAnnotation.Module], ...
     *
     * @param attribute one of this model's [annotationClass]'s [attributes][AnnotationDeclaration.attributes].
     *  Otherwise, behaviour is undefined.
     */
    public fun getValue(attribute: AnnotationDeclaration.Attribute): Value

    /**
     * Tries to make a builtin representation for this annotation for supported builtins.
     *
     * @param which builtin annotation kind to try converting to.
     *
     * @return a builtin annotation model of requested kind,
     * or `null` if the underlying annotation is not a requested builtin.
     */
    public fun <T : BuiltinAnnotation.CanBeCastedOut> asBuiltin(which: BuiltinAnnotation.Target.CanBeCastedOut<T>): T?

    /**
     * Models annotation attribute value.
     */
    public interface Value : HasPlatformModel {
        public interface Visitor<R> {
            public fun visitBoolean(value: Boolean): R
            public fun visitByte(value: Byte): R
            public fun visitShort(value: Short): R
            public fun visitInt(value: Int): R
            public fun visitLong(value: Long): R
            public fun visitChar(value: Char): R
            public fun visitFloat(value: Float): R
            public fun visitDouble(value: Double): R
            public fun visitString(value: String): R
            public fun visitType(value: Type): R
            public fun visitAnnotation(value: Annotation): R
            public fun visitEnumConstant(enum: Type, constant: String): R
            public fun visitArray(value: List<Value>): R
            public fun visitUnresolved(): R
        }

        public fun <R> accept(visitor: Visitor<R>): R
    }
}

