// Copyright 2022 Yandex LLC. All rights reserved.

package com.yandex.daggerlite.process

import java.util.ServiceLoader

internal inline fun <reified S : Any> loadServices(): List<S> {
    val serviceClass = S::class.java
    return ServiceLoader.load(serviceClass, serviceClass.classLoader).toList()
}
