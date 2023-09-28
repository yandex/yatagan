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

package com.yandex.yatagan.testing.procedural

import java.util.EnumMap
import kotlin.random.Random
import kotlin.random.nextInt


/**
 * Maps enum entries to their probability.
 * The main method is [roll], which yields a random [E] entry according to given probabilities.
 */
@JvmInline
value class Distribution<E : Enum<E>> constructor(
    private val map: Map<E, Double>,
) {
    init {
        require(map.values.sum() roughlyEquals 1.0)
    }

    interface Builder<E : Enum<E>> {
        infix fun E.exactly(value: Double)
        fun theRestUniformly()
        fun build(): Distribution<E>
    }

    fun roll(rng: Random): E {
        val number = rng.nextInt(0..1000)
        var current = 0.0
        for ((key, value) in map) {
            current += value * 1000.0
            if (number <= current) {
                return key
            }
        }
        return map.keys.last()
    }

    override fun toString() = buildString {
        append("{")
        map.entries.joinTo(this, separator = ", ") { (key, probability) ->
            "$key: ${probability * 100.0}%"
        }
        append("}")
    }

    companion object {
        fun <E : Enum<E>> builder(clazz: Class<E>): Builder<E> {
            return object : Builder<E> {
                private val map = EnumMap<E, Double>(clazz)

                override fun E.exactly(value: Double) {
                    map[this] = value
                }

                override fun theRestUniformly() {
                    val forTheRest = 1.0 - map.values.sum()
                    val all = clazz.enumConstants
                    if (all.size > map.size) {
                        check(forTheRest >= 0.0)
                        val forOne = forTheRest / (all.size - map.size)
                        for (value in all) {
                            if (value in map) continue
                            map[value] = forOne
                        }
                    }
                }

                override fun build(): Distribution<E> {
                    return Distribution(map)
                }
            }
        }

        inline fun <reified E : Enum<E>> build(block: Builder<E>.() -> Unit): Distribution<E> {
            return builder(E::class.java).apply(block).build()
        }

        inline fun <reified E : Enum<E>> uniform() = build<E> { theRestUniformly() }

        private infix fun Number.roughlyEquals(another: Number): Boolean {
            return toDouble() - another.toDouble() < 0.001f
        }
    }
}
