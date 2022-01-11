package com.yandex.daggerlite

/**
 * A delegate holder for Dagger Lite thread checking for single-thread components.
 *
 * @see Component.multiThreadAccess
 */
object ThreadAssertions {
    fun interface Asserter {
        /**
         * Called on each provider/lazy/entry-point access in a single-thread component to ensure correct thread id.
         */
        fun assertThreadAccess()
    }

    /**
     * An [Asserter] to delegate thread assertions to.
     */
    @JvmField
    @field:Volatile
    var asserter: Asserter? = null

    @JvmStatic
    fun assertThreadAccess() {
        asserter?.assertThreadAccess()
    }
}