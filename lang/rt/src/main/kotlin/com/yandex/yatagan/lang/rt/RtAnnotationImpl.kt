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

import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.Reusable
import com.yandex.yatagan.ValueOf
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Annotation.Value
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.AnnotationBase
import com.yandex.yatagan.lang.common.AnnotationDeclarationBase
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import javax.inject.Qualifier
import javax.inject.Scope

internal class RtAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    private val impl: Annotation,
) : AnnotationBase(), LexicalScope by lexicalScope {

    override val annotationClass: AnnotationDeclaration
        get() = AnnotationClassImpl(impl.javaAnnotationClass)

    override val platformModel: Annotation
        get() = impl

    override fun getValue(attribute: AnnotationDeclaration.Attribute): Value {
        require(attribute is AttributeImpl) { "Invalid attribute type" }
        return try {
            ValueImpl(attribute.impl.invoke(impl))
        } catch (e: ReflectiveOperationException) {
            ValueImpl(null)
        }
    }

    @OptIn(ConditionsApi::class)
    override fun <T : BuiltinAnnotation.CanBeCastedOut> asBuiltin(which: BuiltinAnnotation.Target.CanBeCastedOut<T>): T? {
        return which.modelClass.cast(when(which) {
            BuiltinAnnotation.ValueOf -> RtValueOfAnnotationImpl(impl as? ValueOf ?: return null)
            BuiltinAnnotation.Reusable -> which.takeIf { impl is Reusable || daggerCompat().isReusable(impl) }
        })
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtAnnotationImpl && impl == other.impl)
    }

    override fun hashCode(): Int = impl.hashCode()

    companion object Factory : FactoryKey<Annotation, RtAnnotationImpl> {
        override fun LexicalScope.factory() = ::RtAnnotationImpl
    }

    private inner class ValueImpl(
        private val value: Any?,
    ) : ValueBase() {
        override val platformModel: Any?
            get() = value

        override fun <R> accept(visitor: Value.Visitor<R>): R {
            return when(value) {
                is Boolean -> visitor.visitBoolean(value)
                is Byte -> visitor.visitByte(value)
                is Short -> visitor.visitShort(value)
                is Int -> visitor.visitInt(value)
                is Long -> visitor.visitLong(value)
                is Char -> visitor.visitChar(value)
                is Float -> visitor.visitFloat(value)
                is Double -> visitor.visitDouble(value)
                is ByteArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is ShortArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is IntArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is LongArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is CharArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is FloatArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is DoubleArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is Array<*> -> visitor.visitArray(value.map { ValueImpl(it) })
                is String -> visitor.visitString(value)
                is Class<*> -> visitor.visitType(RtTypeImpl(value))
                is Annotation -> visitor.visitAnnotation(RtAnnotationImpl(value))
                is Enum<*> -> visitor.visitEnumConstant(
                    enum = RtTypeImpl(value.declaringJavaClass),
                    constant = value.name,
                )
                null -> visitor.visitUnresolved()  // Should not be normally reachable
                else -> throw AssertionError("Unexpected value type: $value")
            }
        }

        override fun hashCode() = value.hashCode()
        override fun equals(other: Any?) = this === other || (other is ValueImpl && value == other.value)
    }

    private class AnnotationClassImpl private constructor(
        lexicalScope: LexicalScope,
        private val impl: Class<*>,
    ) : AnnotationDeclarationBase(), Annotated by RtAnnotatedImpl(lexicalScope, impl), LexicalScope by lexicalScope {

        override val attributes: Sequence<AnnotationDeclaration.Attribute> by lazy {
            impl.declaredMethods.asSequence()
                .filter { it.isAbstract }
                .map {
                    AttributeImpl(it)
                }
        }

        override fun isClass(clazz: Class<out Annotation>): Boolean {
            return impl == clazz
        }

        override val qualifiedName: String
            get() = impl.canonicalName

        override fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
            builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>
        ): T? {
            val annotation: BuiltinAnnotation.OnAnnotationClass? = when(builtinAnnotation) {
                BuiltinAnnotation.IntoMap.Key -> (builtinAnnotation as BuiltinAnnotation.IntoMap.Key)
                    .takeIf { impl.isAnnotationPresent(IntoMap.Key::class.java) || daggerCompat().hasMapKey(impl) }
                BuiltinAnnotation.Qualifier -> (builtinAnnotation as BuiltinAnnotation.Qualifier)
                    .takeIf { impl.isAnnotationPresent(Qualifier::class.java) }
                BuiltinAnnotation.Scope -> (builtinAnnotation as BuiltinAnnotation.Scope)
                    .takeIf { impl.isAnnotationPresent(Scope::class.java) }
            }

            return builtinAnnotation.modelClass.cast(annotation)
        }

        override fun getRetention(): AnnotationRetention = AnnotationRetention.RUNTIME

        companion object Factory : FactoryKey<Class<*>, AnnotationClassImpl> {
            override fun LexicalScope.factory() = caching(::AnnotationClassImpl)
        }
    }

    private class AttributeImpl private constructor(
        lexicalScope: LexicalScope,
        val impl: ReflectMethod,
    ) : AnnotationDeclaration.Attribute, LexicalScope by lexicalScope {
        override val name: String
            get() = impl.name

        override val type: Type
            get() = RtTypeImpl(impl.genericReturnType)

        companion object Factory : FactoryKey<ReflectMethod, AttributeImpl> {
            override fun LexicalScope.factory() = ::AttributeImpl
        }
    }
}
