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

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.junit.Test

class AtomicIrGenerationCoordinatorTest {
    @Test
    fun `preparation failure preserves every existing IR declaration by identity`() {
        val file = IrFileImpl(
            fileEntry = NaiveSourceBasedFileEntryImpl("TestCase.kt"),
            symbol = IrFileSymbolImpl(),
            packageFqName = FqName("test"),
        )
        val original = irClass("Original").also(file::addChild)
        val wouldBeGenerated = irClass("YataganFirstComponent")
        val declarationsBefore = file.declarations.toList()
        val preparationFailure = UnsupportedOperationException("unsupported second root")
        var observedPreparationBoundary = false

        val failures = AtomicIrGenerationCoordinator<String, () -> IrClass, IrClass>(
            candidates = listOf("first", "second"),
            prepare = { candidate ->
                when (candidate) {
                    "first" -> ({ wouldBeGenerated.also(file::addChild) })
                    else -> throw preparationFailure
                }
            },
            emit = { _, prepared -> prepared() },
            rollback = { _, declaration -> file.declarations.remove(declaration) },
            preparationObserver = {
                observedPreparationBoundary = true
                assertSameDeclarations(declarationsBefore, file.declarations)
            },
        ).execute()

        assertThat(observedPreparationBoundary).isTrue()
        assertThat(failures).hasSize(1)
        assertThat(failures.single().candidate).isEqualTo("second")
        assertThat(failures.single().error).isSameAs(preparationFailure)
        assertSameDeclarations(declarationsBefore, file.declarations)
        assertThat(file.declarations.single()).isSameAs(original)
    }

    private fun irClass(name: String): IrClass = IrFactoryImpl.buildClass {
        this.name = Name.identifier(name)
    }

    private fun assertSameDeclarations(
        expected: List<Any>,
        actual: List<Any>,
    ) {
        assertThat(actual).hasSameSizeAs(expected)
        expected.zip(actual).forEach { (before, after) ->
            assertThat(after).isSameAs(before)
        }
    }
}
