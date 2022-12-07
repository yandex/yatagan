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

package com.yandex.yatagan.lang

/**
 * Marker for a lang API that may be used for Yatagan implementation and is restricted from client (SPI) usage.
 */
@RequiresOptIn(message = "This API is intended to be used only in yatagan implementation and " +
        "is not designed for client (SPI) usage.")
public annotation class InternalLangApi
