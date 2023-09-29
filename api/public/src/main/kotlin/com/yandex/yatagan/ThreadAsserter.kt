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

@file:JvmName("ThreadAssertions")
package com.yandex.yatagan

/**
 * A delegate holder for Yatagan thread checking for single-thread components.
 *
 * @see Component.multiThreadAccess
 */
public fun interface ThreadAsserter {
    /**
     * Called on each provider/lazy/entry-point access in a single-thread component to ensure correct thread id.
     */
    public fun assertThreadAccess()
}
