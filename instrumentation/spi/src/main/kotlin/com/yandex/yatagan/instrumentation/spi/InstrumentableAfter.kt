package com.yandex.yatagan.instrumentation.spi

import com.yandex.yatagan.instrumentation.Statement

public interface InstrumentableAfter {
    public val after: MutableList<in Statement>

    public companion object {
        public const val INSTANCE_VALUE_NAME: String = "instance"
    }
}