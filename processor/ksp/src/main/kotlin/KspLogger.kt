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

package com.yandex.yatagan.processor.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.yandex.yatagan.processor.common.Logger

internal class KspLogger(
    private val logger: KSPLogger,
) : Logger {
    override fun error(message: String) {
        logger.error(message /*TODO: support where*/)
    }

    override fun warning(message: String) {
        logger.warn(message /*TODO: support where*/)
    }
}