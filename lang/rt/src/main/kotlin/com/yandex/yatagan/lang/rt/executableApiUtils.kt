/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
