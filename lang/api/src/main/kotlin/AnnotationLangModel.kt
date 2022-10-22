package com.yandex.daggerlite.lang

/**
 * Models annotation instance of any class.
 * Suits for modeling annotation of user-defined types or framework annotations, that have no attributes.
 *
 * This interface doesn't expose annotation attributes; to be able to get that, use class-specific annotation model,
 * e. g. [ComponentAnnotationLangModel], ... .
 */
interface AnnotationLangModel : HasPlatformModel {
    /**
     * Annotation class declaration.
     */
    val annotationClass: AnnotationDeclarationLangModel

    /**
     * Get the value of an [attribute], returning default value if no explicit value was specified.
     *
     * Please note, that using this method for obtaining attributes values is only justified for user-defined
     * annotations, whose definition is unknown to the framework.
     * For framework annotations use corresponding concrete annotation models, that may optimize the way they obtain
     *  the attributes, like e.g. [ComponentAnnotationLangModel], [ModuleAnnotationLangModel], ...
     *
     * @param attribute one of this model's [annotationClass]'s [attributes][AnnotationDeclarationLangModel.attributes].
     *  Otherwise behaviour is undefined.
     */
    fun getValue(attribute: AnnotationDeclarationLangModel.Attribute): Value

    /**
     * Models annotation attribute value.
     */
    interface Value : HasPlatformModel {
        interface Visitor<R> {
            fun visitBoolean(value: Boolean): R
            fun visitByte(value: Byte): R
            fun visitShort(value: Short): R
            fun visitInt(value: Int): R
            fun visitLong(value: Long): R
            fun visitChar(value: Char): R
            fun visitFloat(value: Float): R
            fun visitDouble(value: Double): R
            fun visitString(value: String): R
            fun visitType(value: TypeLangModel): R
            fun visitAnnotation(value: AnnotationLangModel): R
            fun visitEnumConstant(enum: TypeLangModel, constant: String): R
            fun visitArray(value: List<Value>): R
            fun visitUnresolved(): R
        }

        fun <R> accept(visitor: Visitor<R>): R
    }
}

