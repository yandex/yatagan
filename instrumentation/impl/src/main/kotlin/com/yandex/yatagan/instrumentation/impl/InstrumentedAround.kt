package com.yandex.yatagan.instrumentation.impl

import com.yandex.yatagan.instrumentation.Statement

interface InstrumentedAround : InstrumentedAfter {
    val before: List<Statement>
}
