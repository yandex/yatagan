package com.yandex.yatagan

/**
 * Annotates an annotation class that denotes a component variant dimension.
 * Any number of [flavors][ComponentFlavor] may be associated with this dimension.
 *
 * @see Component.variant
 */
@VariantApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class ComponentVariantDimension