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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.kcp

import com.yandex.yatagan.lang.kcp.KcpLexicalScope
import org.jetbrains.kotlin.backend.jvm.ir.getIoFile
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName

internal class IncrementalDependencyRecorder(
    private val lookupTracker: LookupTracker?,
    private val expectActualTracker: ExpectActualTracker?,
) {
    /**
     * Tells incremental compilation that this root component file depends on every class its
     * graphs read. The generated implementation lives in the component's own file, yet the file's
     * source references almost none of the graph closure — without these records, editing a
     * binding elsewhere never re-triggers generation and the compiled component goes stale.
     *
     * Member-name lookups catch changes to declarations that exist now; the expect/actual
     * file link additionally covers declarations *added* to a same-module class later (there is
     * no name to look up in advance). Cross-module changes flow through classpath ABI diffing
     * against the recorded lookups.
     */
    fun record(file: IrFile, scope: KcpLexicalScope) {
        if (lookupTracker == null && expectActualTracker == null) return
        val rootIoFile = file.getIoFile()
        val rootPath = rootIoFile?.path ?: file.fileEntry.name
        for (clazz in scope.resolvedClasses) {
            if (clazz.fileOrNull == file) continue
            val classId = clazz.classId ?: continue
            if (lookupTracker != null) {
                lookupTracker.record(
                    filePath = rootPath,
                    position = Position.NO_POSITION,
                    scopeFqName = (classId.outerClassId?.asSingleFqName() ?: classId.packageFqName).asString(),
                    scopeKind = ScopeKind.PACKAGE,
                    name = classId.shortClassName.asString(),
                )
                // The graph reads the whole member surface (annotations included), so any member
                // change may change the graph.
                val classFqName = clazz.kotlinFqName.asString()
                for (declaration in clazz.declarations) {
                    val name = (declaration as? IrDeclarationWithName)?.name ?: continue
                    if (name.isSpecial) continue
                    lookupTracker.record(
                        filePath = rootPath,
                        position = Position.NO_POSITION,
                        scopeFqName = classFqName,
                        scopeKind = ScopeKind.CLASSIFIER,
                        name = name.asString(),
                    )
                }
            }
            if (expectActualTracker != null && rootIoFile != null) {
                val classFile = clazz.fileOrNull?.getIoFile()
                if (classFile != null && classFile != rootIoFile && classFile.isAbsolute) {
                    expectActualTracker.report(expectedFile = classFile, actualFile = rootIoFile)
                }
            }
        }
    }
}
