package com.yandex.yatagan.lang.rt

// Api in this file (effectively - class) wraps Java reflection API available since 1.8 (API 26+ for Android).
// All such API should be wrapped and declared here to avoid runtime class verification issues on Android.
// Read more on the subject:
//  https://chromium.googlesource.com/chromium/src/+/HEAD/build/android/docs/class_verification_failures.md

internal fun ReflectMethod.parameterNames8(): Array<String> {
    val parameters = parameters
    return Array(parameters.size) { index -> parameters[index].name }
}

internal fun ReflectConstructor.parameterNames8(): Array<String> {
    val parameters = parameters
    return Array(parameters.size) { index -> parameters[index].name }
}

internal fun ReflectMethod.parameterCount8(): Int {
    return parameterCount
}

internal fun ReflectConstructor.parameterCount8(): Int {
    return parameterCount
}
