package com.yandex.daggerlite.lang.rt

import java.lang.reflect.Executable

internal object ExecutableSignatureComparator : Comparator<Executable> {
    override fun compare(one: Executable, other: Executable): Int {
        one.name.compareTo(other.name).let { if (it != 0) return it }

        val myParameterTypes = one.parameterTypes
        val otherParameterTypes = other.parameterTypes
        myParameterTypes.size.compareTo(otherParameterTypes.size).let { if (it != 0) return it }
        for (i in myParameterTypes.indices) {
            myParameterTypes[i].canonicalName
                .compareTo(otherParameterTypes[i].canonicalName).let { if (it != 0) return it }
        }

        return 0
    }
}