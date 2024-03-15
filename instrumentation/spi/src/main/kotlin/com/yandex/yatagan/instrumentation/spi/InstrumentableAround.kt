package com.yandex.yatagan.instrumentation.spi

import com.yandex.yatagan.instrumentation.Statement

public interface InstrumentableAround : InstrumentableAfter {
    public val before: MutableList<in Statement>
}