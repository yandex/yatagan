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

@file:JvmName("Checks")

package com.yandex.yatagan.internal

import kotlin.contracts.contract

@PublishedApi
internal fun assertNotNull(instance: Any?, message: String) {
    contract {
        returns() implies (instance != null)
    }
    if (instance == null) {
        throw IllegalStateException(message)
    }
}

@PublishedApi
internal fun <T : Any> checkInputNotNull(input: T?): T {
    assertNotNull(input, "Component input is null or unspecified")
    return input
}

@PublishedApi
internal fun <T : Any> checkProvisionNotNull(instance: T?): T {
    assertNotNull(instance, "Provision result is null")
    return instance
}