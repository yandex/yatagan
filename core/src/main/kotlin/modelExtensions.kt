package com.yandex.daggerlite.core

val ComponentFactoryModel.allInputs get() = factoryInputs.asSequence() + builderInputs.asSequence()