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

package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName

internal object Names {
    val Lazy: ClassName = ClassName.get("com.yandex.yatagan", "Lazy")
    val LazyCompat: ClassName = ClassName.get("dagger", "Lazy")
    val Provider: ClassName = ClassName.get("javax.inject", "Provider")
    val Optional: ClassName = ClassName.get("com.yandex.yatagan", "Optional")
    val AutoBuilder: ClassName = ClassName.get("com.yandex.yatagan", "AutoBuilder")
    val ThreadAssertions: ClassName = ClassName.get("com.yandex.yatagan.internal", "ThreadAssertions")
    val Checks: ClassName = ClassName.get("com.yandex.yatagan.internal", "Checks")
    val YataganGenerated = ClassName.get("com.yandex.yatagan.internal", "YataganGenerated")

    val AssertionError: ClassName = ClassName.get(java.lang.AssertionError::class.java)
    val Class: ClassName = ClassName.get(java.lang.Class::class.java)
    val Collections: ClassName = ClassName.get(java.util.Collections::class.java)
    val Arrays: ClassName = ClassName.get(java.util.Arrays::class.java)
    val ArrayList: ClassName = ClassName.get(java.util.ArrayList::class.java)
    val HashMap: ClassName = ClassName.get(java.util.HashMap::class.java)
    val HashSet: ClassName = ClassName.get(java.util.HashSet::class.java)

    val GeneratedJava8: ClassName = ClassName.get("javax.annotation", "Generated")
    val GeneratedJava9Plus: ClassName = ClassName.get("javax.annotation.processing", "Generated")
}
