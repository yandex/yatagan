package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.AbstractAnnotationValueVisitor8

internal class JavaxAnnotationImpl private constructor(
    private val impl: AnnotationMirror,
) : CtAnnotationLangModel {
    override val annotationClass: AnnotationDeclarationLangModel by lazy {
        JavaxAnnotationClassImpl(impl.annotationType.asTypeElement())
    }

    override fun getBoolean(attribute: String): Boolean {
        return impl.booleanValue(param = attribute)
    }

    override fun getTypes(attribute: String): Sequence<TypeLangModel> {
        return impl.typesValue(param = attribute).map { JavaxTypeImpl(it) }.memoize()
    }

    override fun getType(attribute: String): TypeLangModel {
        return JavaxTypeImpl(impl.typeValue(param = attribute))
    }

    override fun getString(attribute: String): String {
        return impl.stringValue(param = attribute)
    }

    override fun getAnnotations(attribute: String): Sequence<CtAnnotationLangModel> {
        return impl.annotationsValue(param = attribute).map { Factory(it) }.memoize()
    }

    override fun getAnnotation(attribute: String): CtAnnotationLangModel {
        return Factory(impl.annotationValue(param = attribute))
    }

    override fun toString() = formatString(impl)


    companion object Factory : ObjectCache<AnnotationMirrorEquivalence, JavaxAnnotationImpl>() {
        operator fun invoke(impl: AnnotationMirror) = createCached(AnnotationMirrorEquivalence(impl)) {
            JavaxAnnotationImpl(impl)
        }

        private fun formatString(value: AnnotationMirror): String = buildString {
            append('@')
            append(value.annotationType.asTypeElement().qualifiedName.toString())
            val elementValues = value.elementValues
            if (elementValues.isNotEmpty()) {
                elementValues.entries
                    .sortedBy { (key, _) -> key.simpleName.toString() }
                    .joinTo(this, prefix = "(", postfix = ")") { (key, value) ->
                        val result = value.accept(object : AbstractAnnotationValueVisitor8<String, Unit>() {
                            override fun visitBoolean(b: Boolean, p: Unit?) = b.toString()
                            override fun visitByte(b: Byte, p: Unit?) = b.toString()
                            override fun visitChar(c: Char, p: Unit?) = c.toString()
                            override fun visitDouble(d: Double, p: Unit?) = d.toString()
                            override fun visitFloat(f: Float, p: Unit?) = f.toString()
                            override fun visitInt(i: Int, p: Unit?) = i.toString()
                            override fun visitLong(i: Long, p: Unit?) = i.toString()
                            override fun visitShort(s: Short, p: Unit?) = s.toString()
                            override fun visitString(s: String, p: Unit?) = "\"$s\""
                            override fun visitType(t: TypeMirror, p: Unit?) = JavaxTypeImpl(t).toString()
                            override fun visitAnnotation(a: AnnotationMirror, p: Unit?): String = formatString(a)

                            override fun visitEnumConstant(c: VariableElement, p: Unit?): String {
                                val enum = c.enclosingElement.asTypeElement()
                                return "${enum.qualifiedName}.${c.simpleName}"
                            }

                            override fun visitArray(vals: List<AnnotationValue>, p: Unit?): String {
                                return vals.joinToString(prefix = "{", postfix = "}") {
                                    it.accept(this)
                                }
                            }
                        })
                        "${key.simpleName}=$result"
                    }
            }
        }
    }
}
