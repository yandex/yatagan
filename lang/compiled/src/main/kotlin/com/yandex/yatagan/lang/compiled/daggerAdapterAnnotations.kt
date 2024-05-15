/*
 * Copyright 2024 Yandex LLC
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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.BuiltinAnnotation

// dagger.Component
internal class CtComponentAnnotationDaggerCompatImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Component {
    override val isRoot get() = true
    override val modules get() = impl.getTypes("modules")
    override val dependencies get() = impl.getTypes("dependencies")
    override val variant get() = emptyList<Nothing>()
    override val multiThreadAccess get() = true
}

// dagger.Subcomponent
internal class CtSubcomponentAnnotationDaggerCompatImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.Component {
    override val isRoot get() = false
    override val modules get() = impl.getTypes("modules")
    override val dependencies get() = emptyList<Nothing>()  // No dependencies
    override val variant get() = emptyList<Nothing>()
    override val multiThreadAccess get() = true
}

// dagger.multibindings.IntoSet
internal class CtIntoSetAnnotationDaggerCompatImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
    override val flatten get() = false
}

// dagger.multibindings.ElementsIntoSet
internal class CtElementsIntoSetAnnotationDaggerCompatImpl(
    impl: CtAnnotationBase,
) : CtBuiltinAnnotationBase(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
    override val flatten get() = true
}