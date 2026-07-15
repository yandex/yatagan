/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.kcp

/** Coordinates preparation of every target before any target is emitted. */
internal class AtomicIrGenerationCoordinator<C, P, E>(
    private val candidates: List<C>,
    private val prepare: (C) -> P,
    private val emit: (C, P) -> E,
    private val rollback: (C, E) -> Unit,
    private val preparationObserver: () -> Unit = {},
) {
    fun execute(): List<Failure<C>> {
        val prepared = mutableListOf<Pair<C, P>>()
        val preparationFailures = mutableListOf<Failure<C>>()
        for (candidate in candidates) {
            try {
                prepared += candidate to prepare(candidate)
            } catch (e: Throwable) {
                preparationFailures += Failure(candidate, e)
            }
        }
        preparationObserver()
        if (preparationFailures.isNotEmpty()) {
            return preparationFailures
        }

        val emitted = mutableListOf<Pair<C, E>>()
        for ((candidate, preparedCandidate) in prepared) {
            try {
                emitted += candidate to emit(candidate, preparedCandidate)
            } catch (e: Throwable) {
                for ((emittedCandidate, result) in emitted.asReversed()) {
                    rollback(emittedCandidate, result)
                }
                return listOf(Failure(candidate, e))
            }
        }
        return emptyList()
    }

    data class Failure<C>(
        val candidate: C,
        val error: Throwable,
    )
}
