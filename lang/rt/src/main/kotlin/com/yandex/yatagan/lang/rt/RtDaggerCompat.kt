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

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.scope.LexicalScope
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.Subcomponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.lang.reflect.AnnotatedElement

internal interface RtDaggerCompat {
    fun hasAssistedInject(c: AnnotatedElement): Boolean
    fun hasAssistedFactory(c: AnnotatedElement): Boolean
    fun hasComponentBuilder(c: AnnotatedElement): Boolean
    fun hasBinds(c: AnnotatedElement): Boolean
    fun hasBindsInstance(c: AnnotatedElement): Boolean
    fun hasProvides(c: AnnotatedElement): Boolean
    fun hasIntoMap(c: AnnotatedElement): Boolean
    fun hasMultibinds(c: AnnotatedElement): Boolean
    fun hasMapKey(c: AnnotatedElement): Boolean

    fun isReusable(c: ReflectAnnotation): Boolean
    fun isBindsInstance(c: ReflectAnnotation): Boolean
    fun asAssisted(c: ReflectAnnotation): BuiltinAnnotation.Assisted?

    fun getIntoSet(c: AnnotatedElement): BuiltinAnnotation.IntoCollectionFamily.IntoSet?
    fun getModule(c: AnnotatedElement): BuiltinAnnotation.Module?
    fun getComponent(c: AnnotatedElement): BuiltinAnnotation.Component?

    companion object : Extensible.Key<RtDaggerCompat, LexicalScope.Extensions>

    // WARNING: Do not reference this class if there's no guarantee that the dagger api is in the classpath.
    class Impl(
        scope: LexicalScope,
    ) : RtDaggerCompat, LexicalScope by scope {
        override fun hasAssistedInject(c: AnnotatedElement) =
            c.isAnnotationPresent(AssistedInject::class.java)

        override fun hasAssistedFactory(c: AnnotatedElement) =
            c.isAnnotationPresent(AssistedFactory::class.java)

        override fun hasComponentBuilder(c: AnnotatedElement) =
            c.isAnnotationPresent(Component.Builder::class.java) ||
                    c.isAnnotationPresent(Component.Factory::class.java) ||
                    c.isAnnotationPresent(Subcomponent.Builder::class.java) ||
                    c.isAnnotationPresent(Subcomponent.Factory::class.java)

        override fun hasBinds(c: AnnotatedElement) = c.isAnnotationPresent(Binds::class.java)
        override fun hasBindsInstance(c: AnnotatedElement) = c.isAnnotationPresent(BindsInstance::class.java)
        override fun hasProvides(c: AnnotatedElement) = c.isAnnotationPresent(Provides::class.java)
        override fun hasIntoMap(c: AnnotatedElement) = c.isAnnotationPresent(IntoMap::class.java)
        override fun hasMultibinds(c: AnnotatedElement) = c.isAnnotationPresent(Multibinds::class.java)
        override fun hasMapKey(c: AnnotatedElement) = c.isAnnotationPresent(MapKey::class.java)

        override fun isReusable(c: ReflectAnnotation) = c is Reusable
        override fun isBindsInstance(c: ReflectAnnotation) = c is BindsInstance

        override fun asAssisted(c: ReflectAnnotation): BuiltinAnnotation.Assisted? = when(c) {
            is Assisted -> RtAssistedAnnotationDaggerCompatImpl(c)
            else -> null
        }

        override fun getModule(c: AnnotatedElement): BuiltinAnnotation.Module? =
            c.getDeclaredAnnotation(Module::class.java)
                ?.let { RtModuleAnnotationDaggerCompatImpl(it) }

        override fun getComponent(c: AnnotatedElement): BuiltinAnnotation.Component? =
            c.getDeclaredAnnotation(Component::class.java)
                ?.let { RtComponentAnnotationDaggerCompatImpl(it) }
                ?: c.getDeclaredAnnotation(Subcomponent::class.java)
                    ?.let { RtSubcomponentAnnotationDaggerCompatImpl(it) }

        override fun getIntoSet(c: AnnotatedElement): BuiltinAnnotation.IntoCollectionFamily.IntoSet? =
            c.getDeclaredAnnotation(IntoSet::class.java)?.let { RtIntoSetAnnotationDaggerCompatImpl(it) }
                ?: c.getDeclaredAnnotation(ElementsIntoSet::class.java)
                    ?.let { RtElementsIntoSetAnnotationDaggerCompatImpl(it) }

        private inner class RtComponentAnnotationDaggerCompatImpl(
            impl: Component,
        ) : RtAnnotationImplBase<Component>(impl), BuiltinAnnotation.Component, LexicalScope by this {
            override val isRoot: Boolean get() = true
            override val modules get() = impl.modules.map { RtTypeImpl(it.java) }
            override val dependencies get() = impl.dependencies.map { RtTypeImpl(it.java) }
            override val variant get() = emptyList<Nothing>()
            override val multiThreadAccess: Boolean get() = true
        }

        private inner class RtSubcomponentAnnotationDaggerCompatImpl(
            impl: Subcomponent,
        ) : RtAnnotationImplBase<Subcomponent>(impl), BuiltinAnnotation.Component, LexicalScope by this {
            override val isRoot: Boolean get() = false
            override val modules get() = impl.modules.map { RtTypeImpl(it.java) }
            override val dependencies get() = emptyList<Nothing>()
            override val variant get() = emptyList<Nothing>()
            override val multiThreadAccess: Boolean get() = true
        }

        private inner class RtModuleAnnotationDaggerCompatImpl (
            impl: Module,
        ) : RtAnnotationImplBase<Module>(impl), BuiltinAnnotation.Module, LexicalScope by this {
            override val includes get() = impl.includes.map { RtTypeImpl(it.java) }
            override val subcomponents get() = impl.subcomponents.map { RtTypeImpl(it.java) }
        }

        private class RtIntoSetAnnotationDaggerCompatImpl(
            impl: IntoSet,
        ) : RtAnnotationImplBase<IntoSet>(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
            override val flatten: Boolean
                get() = false
        }

        private class RtElementsIntoSetAnnotationDaggerCompatImpl(
            impl: ElementsIntoSet,
        ) : RtAnnotationImplBase<ElementsIntoSet>(impl), BuiltinAnnotation.IntoCollectionFamily.IntoSet {
            override val flatten: Boolean
                get() = true
        }

        private class RtAssistedAnnotationDaggerCompatImpl(
            impl: Assisted,
        ) : RtAnnotationImplBase<Assisted>(impl), BuiltinAnnotation.Assisted {
            override val value: String
                get() = impl.value
        }
    }

    class Stub : RtDaggerCompat {
        override fun hasAssistedInject(c: AnnotatedElement) = false
        override fun hasAssistedFactory(c: AnnotatedElement) = false
        override fun hasComponentBuilder(c: AnnotatedElement) = false
        override fun hasBinds(c: AnnotatedElement) = false
        override fun hasBindsInstance(c: AnnotatedElement) = false
        override fun hasProvides(c: AnnotatedElement) = false
        override fun hasIntoMap(c: AnnotatedElement) = false
        override fun hasMultibinds(c: AnnotatedElement) = false
        override fun hasMapKey(c: AnnotatedElement) = false
        override fun isReusable(c: ReflectAnnotation) = false
        override fun isBindsInstance(c: ReflectAnnotation) = false
        override fun asAssisted(c: ReflectAnnotation) = null
        override fun getIntoSet(c: AnnotatedElement) = null
        override fun getModule(c: AnnotatedElement) = null
        override fun getComponent(c: AnnotatedElement) = null
    }
}
