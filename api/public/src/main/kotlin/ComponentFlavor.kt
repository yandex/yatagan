package com.yandex.yatagan

import kotlin.reflect.KClass

/**
 * Annotates an annotation class that denotes a component flavor.
 * The flavor must be associated with a [dimension][ComponentVariantDimension].
 *
 * It's a good practice to declare all basic flavors for a dimension inside the dimension declaration for better
 * visual association.
 */
@VariantApi
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ComponentFlavor(
    /**
     * Dimension, that this flavor belongs to.
     */
    val dimension: KClass<out Annotation>,
)