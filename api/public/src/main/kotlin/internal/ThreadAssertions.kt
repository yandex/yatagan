package com.yandex.yatagan.internal

import com.yandex.yatagan.ThreadAsserter

public object ThreadAssertions {
    @field:Volatile
    private var asserter: ThreadAsserter? = null

    @JvmStatic
    public fun setAsserter(asserter: ThreadAsserter?) {
        this.asserter = asserter
    }

    @JvmStatic
    public fun assertThreadAccess() {
        asserter?.assertThreadAccess()
    }
}