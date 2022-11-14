package com.yandex.yatagan.lang

/**
 * An annotation class declaration.
 */
public interface AnnotationDeclaration : Annotated {
    /**
     * Represents an annotation class' property/@interface's method.
     */
    public interface Attribute {
        /**
         * Name of the attribute.
         */
        public val name: String

        /**
         * Type of the attribute.
         */
        public val type: Type
    }

    /**
     * Checks whether the annotation class is given JVM type.
     *
     * @param clazz Java class to check
     */
    public fun isClass(clazz: Class<out kotlin.Annotation>): Boolean {
        return clazz.canonicalName == qualifiedName
    }

    /**
     * Qualified name of the annotation class.
     */
    public val qualifiedName: String

    /**
     * Attributes (annotation class' properties for Kotlin and @interface's methods for Java).
     */
    public val attributes: Sequence<Attribute>

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    public fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
        builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>
    ): T?

    /**
     * Computes annotation retention.
     *
     * @return annotation retention in Kotlin terms.
     *
     * @see java.lang.annotation.Retention
     * @see kotlin.annotation.Retention
     */
    public fun getRetention(): AnnotationRetention
}