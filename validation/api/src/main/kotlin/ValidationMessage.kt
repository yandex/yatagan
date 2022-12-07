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

package com.yandex.yatagan.validation

/**
 * Validation message, issued by [MayBeInvalid].
 * Should have reasonable [equals]/[hashCode] implementation for grouping.
 */
public interface ValidationMessage {
    /**
     * Validation message Kind.
     */
    public enum class Kind {
        /**
         * Fatal message, if any message of such kind is issued, a processing will be marked as failed.
         */
        Error,

        /**
         * Serious warning, that can be turned into error in "strict mode".
         */
        MandatoryWarning,

        /**
         * Warning message, non-fatal.
         */
        Warning,
    }

    /**
     * Validation message Kind.
     */
    public val kind: Kind

    /**
     * Message text. Maybe [RichString] or any other [CharSequence].
     */
    public val contents: CharSequence

    /**
     * Notes, related to the message, helping/clarifying/adding more info.
     */
    public val notes: Collection<CharSequence>
}