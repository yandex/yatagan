package com.yandex.yatagan.lang

/**
 * Models a [Callable] parameter.
 */
public interface Parameter : Annotated {
    /**
     * Parameter name.
     *
     * _WARNING_: this property should not be relied on, as parameter names' availability may vary.
     *  It's generally safe to use this for error reporting or for method overriding; yet code correctness and public
     *  generated API must not depend on parameter names.
     */
    public val name: String

    /**
     * Parameter type.
     */
    public val type: Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    public fun <T : BuiltinAnnotation.OnParameter> getAnnotation(
        which: BuiltinAnnotation.Target.OnParameter<T>
    ): T?
}