package com.yandex.yatagan.internal

import com.yandex.yatagan.ThreadAsserter

object ThreadAssertions {
    @field:Volatile
    private var asserter: ThreadAsserter? = null

    @JvmStatic
    fun setAsserter(asserter: ThreadAsserter?) {
        this.asserter = asserter
    }

    @JvmStatic
    fun assertThreadAccess() {
        asserter?.assertThreadAccess()
    }
}