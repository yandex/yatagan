package com.yandex.daggerlite.lang.rt

internal object MethodSignatureComparator : Comparator<ReflectMethod> {
    override fun compare(one: ReflectMethod, other: ReflectMethod): Int {
        one.name.compareTo(other.name).let { if (it != 0) return it }

        return parametersCompare(one.parameterTypes, other.parameterTypes)
    }
}

internal object ConstructorSignatureComparator : Comparator<ReflectConstructor> {
    override fun compare(one: ReflectConstructor, other: ReflectConstructor): Int {
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