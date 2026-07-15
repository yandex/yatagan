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

package com.yandex.yatagan.codegen.ir

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.ThreadChecker
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.addChild

/**
 * Emits the compiled implementation hierarchy for an already resolved root [BindingGraph].
 *
 * [prepare] completes the full support check and builds all IR off-tree without attaching it.
 */
class IrComponentEmitter(
    private val pluginContext: IrPluginContext,
    private val targetFile: IrFile,
    private val graph: BindingGraph,
    private val options: Options = Options(),
) {
    class Options(
        val enableProvisionNullChecks: Boolean = true,
        val threadChecker: ThreadChecker? = null,
    )

    /**
     * Checks the complete graph and returns a prepared emission that can be executed later.
     *
     * @throws UnsupportedIrGraphException if any part of the graph is outside the supported subset.
     */
    fun prepare(): PreparedIrComponent {
        val emitter = ComponentTreeEmitter(pluginContext, targetFile, graph, options)
        val implementation = emitter.buildOffTree()
        return PreparedIrComponent {
            targetFile.addChild(implementation)
            implementation
        }
    }

    /** Adds the loader-named implementation to [targetFile]. */
    fun emit(): IrClass = prepare().emit()

    class PreparedIrComponent internal constructor(
        emission: () -> IrClass,
    ) {
        private val implementation: IrClass by lazy(LazyThreadSafetyMode.NONE, emission)

        /** Attaches the implementation after successful preparation. */
        fun emit(): IrClass = implementation
    }
}

/** Raised when [IrComponentEmitter] is asked to emit semantics outside its supported subset. */
class UnsupportedIrGraphException(
    message: String,
) : IllegalArgumentException(message)

internal fun unsupported(reason: String): Nothing = throw UnsupportedIrGraphException(reason)
