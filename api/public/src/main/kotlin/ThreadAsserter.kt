@file:JvmName("ThreadAssertions")
package com.yandex.yatagan

/**
 * A delegate holder for Yatagan thread checking for single-thread components.
 *
 * @see Component.multiThreadAccess
 */
fun interface ThreadAsserter {
    /**
     * Called on each provider/lazy/entry-point access in a single-thread component to ensure correct thread id.
     */
    fun assertThreadAccess()
}
