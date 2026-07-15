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

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility

internal val LexicalScope.kcpScope: KcpLexicalScope
    get() = ext[KcpScopeKey]

internal val IrDeclarationWithVisibility.isEffectivelyPublic: Boolean
    get() = visibility == DescriptorVisibilities.PUBLIC || visibility == DescriptorVisibilities.INTERNAL

internal val IrDeclarationWithVisibility.isNonPrivate: Boolean
    get() = !DescriptorVisibilities.isPrivate(visibility)
