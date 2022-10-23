package com.yandex.daggerlite.lang.rt

// NOTE: Do not use java.lang.reflect.* API directly throughout the implementation, use these aliases instead.
//  This is done to avoid named clashes with lang-api.

// Entities:

internal typealias ReflectMember = java.lang.reflect.Member
internal typealias ReflectMethod = java.lang.reflect.Method
internal typealias ReflectField = java.lang.reflect.Field
internal typealias ReflectConstructor = java.lang.reflect.Constructor<*>

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