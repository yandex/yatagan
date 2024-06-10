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

// NOTE: Do not use java.lang.reflect.* API directly throughout the implementation, use these aliases instead.
//  This is done to avoid named clashes with lang-api.

// Entities:

internal typealias ReflectMember = java.lang.reflect.Member
internal typealias ReflectMethod = java.lang.reflect.Method
internal typealias ReflectField = java.lang.reflect.Field
internal typealias ReflectConstructor = java.lang.reflect.Constructor<*>

@Suppress("RemoveRedundantQualifierName")
internal typealias ReflectAnnotation = kotlin.Annotation

// Types:

internal typealias ReflectAnnotatedElement = java.lang.reflect.AnnotatedElement
internal typealias ReflectType = java.lang.reflect.Type
internal typealias ReflectGenericArrayType = java.lang.reflect.GenericArrayType
internal typealias ReflectParameterizedType = java.lang.reflect.ParameterizedType
internal typealias ReflectTypeVariable = java.lang.reflect.TypeVariable<*>
internal typealias ReflectWildcardType = java.lang.reflect.WildcardType

// Misc:

internal typealias ArraysReflectionUtils = java.lang.reflect.Array
internal typealias ReflectModifier = java.lang.reflect.Modifier