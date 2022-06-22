package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel.Value
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.AbstractAnnotationValueVisitor8
import javax.lang.model.util.ElementFilter

internal class JavaxAnnotationImpl private constructor(
    private val impl: AnnotationMirror,
) : CtAnnotationLangModel() {
    override val annotationClass: AnnotationDeclarationLangModel by lazy {
        AnnotationClassImpl(impl.annotationType.asTypeElement())
    }

    override val platformModel: AnnotationMirror
        get() = impl

    override fun getValue(attribute: AnnotationDeclarationLangModel.Attribute): Value {
        require(attribute is AttributeImpl) { "Invalid attribute type" }
        val value = impl.elementValues[attribute.impl] ?: attribute.impl.defaultValue
        checkNotNull(value) { "Attribute missing/invalid" }
        return ValueImpl(
            value = value,
            type = attribute.impl.returnType,
        )
    }

    companion object Factory : ObjectCache<AnnotationMirrorEquivalence, JavaxAnnotationImpl>() {
        operator fun invoke(impl: AnnotationMirror) = createCached(AnnotationMirrorEquivalence(impl)) {
            JavaxAnnotationImpl(impl)
        }
    }

    private class ValueImpl(
        private val value: AnnotationValue,
        private val type: TypeMirror,
    ) : ValueBase() {
        private val equivalence = AnnotationValueEquivalence(value)

        override val platformModel: AnnotationValue
            get() = value

        override fun <R> accept(visitor: Value.Visitor<R>): R {
            return value.accept(object : AbstractAnnotationValueVisitor8<R, Unit>() {
                override fun visitBoolean(b: Boolean, p: Unit?): R = visitor.visitBoolean(b)
                override fun visitByte(b: Byte, p: Unit?): R = visitor.visitByte(b)
                override fun visitChar(c: Char, p: Unit?): R = visitor.visitChar(c)
                override fun visitDouble(d: Double, p: Unit?): R = visitor.visitDouble(d)
                override fun visitFloat(f: Float, p: Unit?): R = visitor.visitFloat(f)
                override fun visitInt(i: Int, p: Unit?): R = visitor.visitInt(i)
                override fun visitLong(i: Long, p: Unit?): R = visitor.visitLong(i)
                override fun visitShort(s: Short, p: Unit?): R = visitor.visitShort(s)
                override fun visitString(s: String, p: Unit?): R {
                    return if (s == "<error>" &&
                        (type.kind != TypeKind.DECLARED || type.asTypeElement() != Utils.stringType)) {
                        // If the type is not string, yet the value is the "<error>" string, then it actually
                        //  represents an unresolved type.
                        visitor.visitType(JavaxTypeImpl(Utils.types.nullType))
                    } else {
                        visitor.visitString(s)
                    }
                }
                override fun visitType(t: TypeMirror, p: Unit?): R = visitor.visitType(JavaxTypeImpl(t))
                override fun visitAnnotation(a: AnnotationMirror, p: Unit?): R = visitor.visitAnnotation(Factory(a))
                override fun visitEnumConstant(c: VariableElement, p: Unit?): R {
                    val enum = JavaxTypeImpl(c.enclosingElement.asTypeElement().asType().asDeclaredType())
                    return visitor.visitEnumConstant(enum, c.simpleName.toString())
                }
                override fun visitArray(vals: List<AnnotationValue>, p: Unit?): R {
                    val arrayType = type.asArrayType()
                    return visitor.visitArray(vals.map {
                        ValueImpl(value = it, type = arrayType.componentType)
                    })
                }
                override fun visitUnknown(av: AnnotationValue?, p: Unit?): R = visitor.visitUnresolved()
            }, Unit)
        }

        override fun hashCode() = equivalence.hashCode()
        override fun equals(other: Any?) = this === other || (other is ValueImpl && equivalence == other.equivalence)
    }

    private class AttributeImpl(
        val impl: ExecutableElement,
    ) : AnnotationDeclarationLangModel.Attribute {
        override val name: String
            get() = impl.simpleName.toString()
        override val type: TypeLangModel
            get() = JavaxTypeImpl(impl.returnType)
    }

    private class AnnotationClassImpl private constructor(
        private val impl: TypeElement,
    ) : AnnotationDeclarationLangModel, AnnotatedLangModel by JavaxAnnotatedImpl(impl) {

        override val attributes: Sequence<AnnotationDeclarationLangModel.Attribute> by lazy {
            ElementFilter.methodsIn(impl.enclosedElements)
                .asSequence()
                .filter { it.isAbstract }
                .map {
                    AttributeImpl(it)
                }
                .memoize()
        }

        override fun isClass(clazz: Class<out Annotation>): Boolean {
            return impl.qualifiedName.contentEquals(clazz.canonicalName)
        }

        override fun toString(): String = impl.qualifiedName.toString()

        companion object Factory : ObjectCache<TypeElement, AnnotationClassImpl>() {
            operator fun invoke(type: TypeElement) = createCached(type) { AnnotationClassImpl(type) }
        }
    }
}
