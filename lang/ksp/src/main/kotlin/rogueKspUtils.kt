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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe


internal fun Resolver.getKotlinClassByName(qualifiedName: KSName, forceMutable: Boolean): KSClassDeclaration? {
    // FIXME: Remove this ksp-impl workaround when this is available in the public api.
    var name = mapJavaNameToKotlin(qualifiedName) ?: qualifiedName
    if (forceMutable) {
        val mutable = JavaToKotlinClassMap.readOnlyToMutable(FqNameUnsafe(name.asString()))
        if (mutable != null) {
            name = getKSNameFromString(mutable.asString())
        }
    }
    return getClassDeclarationByName(name)
}