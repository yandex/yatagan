package com.yandex.yatagan

import com.yandex.yatagan.common.loadImplementationByBuilderClass
import com.yandex.yatagan.common.loadImplementationByComponentClass
import com.yandex.yatagan.internal.ThreadAssertions

/**
 * Yatagan entry-point object. Create instances of Yatagan components by loading generated implementations for
 * the given components/builders classes.
 *
 * Use either [builder] or [create].
 */
object Yatagan {

    /**
     * Sets [ThreadAsserter] object to be used in Single Thread component implementations.
     */
    @JvmStatic
    fun setThreadAsserter(threadAsserter: ThreadAsserter?) {
        ThreadAssertions.setAsserter(threadAsserter)
    }

    /**
     * Use this to create a component builder instance for root components that declare it.
     *
     * @param builderClass component builder class
     * @return ready component builder instance of the given class
     *
     * @see Component.Builder
     */
    @JvmStatic
    fun <T : Any> builder(builderClass: Class<T>): T {
        return loadImplementationByBuilderClass(builderClass)
    }

    /**
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    fun<T : Any> create(componentClass: Class<T>): T {
        return loadImplementationByComponentClass(componentClass)
    }
}