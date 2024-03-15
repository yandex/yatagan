package com.yandex.yatagan.instrumentation.impl

import com.yandex.yatagan.instrumentation.Statement

interface InstrumentedAfter {
    val after: List<Statement>
}
