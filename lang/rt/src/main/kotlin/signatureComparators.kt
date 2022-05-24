package com.yandex.daggerlite.lang.rt

import java.lang.reflect.Constructor
import java.lang.reflect.Method

internal object MethodSignatureComparator : Comparator<Method> {
    override fun compare(one: Method, other: Method): Int {
        one.name.compareTo(other.name).let { if (it != 0) return it }

        return parametersCompare(one.parameterTypes, other.parameterTypes)
    }
}

internal object ConstructorSignatureComparator : Comparator<Constructor<*>> {
    override fun compare(one: Constructor<*>, other: Constructor<*>): Int {
        return parametersCompare(one.parameterTypes, other.parameterTypes)
    }
}

private fun parametersCompare(myParameterTypes: Array<Class<*>>, otherParameterTypes: Array<Class<*>>): Int {
    myParameterTypes.size.compareTo(otherParameterTypes.size).let { if (it != 0) return it }
    for (i in myParameterTypes.indices) {
        myParameterTypes[i].canonicalName
            .compareTo(otherParameterTypes[i].canonicalName).let { if (it != 0) return it }
    }
    return 0;
}