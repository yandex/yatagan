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

package com.yandex.yatagan.validation.impl

internal object CharSequenceComparator : Comparator<CharSequence> {
    override fun compare(one: CharSequence, another: CharSequence): Int {
        one.length.compareTo(another.length).let { if (it != 0) return it }
        for (index in one.indices) {
            one[index].compareTo(another[index]).let { if (it != 0) return it }
        }
        return 0
    }
}

internal object PathComparator : Comparator<List<CharSequence>> {
    override fun compare(one: List<CharSequence>, another: List<CharSequence>): Int {
        one.size.compareTo(another.size).let { if (it != 0) return it }
        for (index in one.indices.reversed()) {  // reversed is speculative optimization here
            CharSequenceComparator.compare(one[index], another[index]).let { if (it != 0) return it }
        }
        return 0
    }
}
