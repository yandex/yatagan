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

package com.yandex.yatagan.lang

/**
 * Marker interface for all framework (builtin) annotations.
 *
 * These are modeled separately from [Annotated] API, as some implementation may optimize working with _known_
 *  annotation classes and/or handle their names in a specific way.
 */
public sealed interface BuiltinAnnotation{
    /**
     * A marker interface for builtin annotation class.
     * All direct sub-interfaces denote a specific annotation target.
     */
    public sealed interface Target<T : BuiltinAnnotation> {
        public val modelClass: Class<out T>

        public sealed interface OnMethod<T : BuiltinAnnotation.OnMethod> : Target<T>
        public sealed interface OnMethodRepeatable<T : BuiltinAnnotation.OnMethodRepeatable> : Target<T>

        public sealed interface OnConstructor<T : BuiltinAnnotation.OnConstructor> : Target<T>

        public sealed interface OnClass<T : BuiltinAnnotation.OnClass> : Target<T>
        public sealed interface OnClassRepeatable<T : BuiltinAnnotation.OnClassRepeatable> : Target<T>

        public sealed interface OnAnnotationClass<T : BuiltinAnnotation.OnAnnotationClass> : Target<T>

        public sealed interface OnParameter<T : BuiltinAnnotation.OnParameter> : Target<T>

        public sealed interface OnField<T : BuiltinAnnotation.OnField> : Target<T>
    }

    public sealed interface OnMethod : BuiltinAnnotation
    public sealed interface OnMethodRepeatable : BuiltinAnnotation

    public sealed interface OnConstructor : BuiltinAnnotation

    public sealed interface OnClass : BuiltinAnnotation
    public sealed interface OnClassRepeatable : BuiltinAnnotation

    public sealed interface OnAnnotationClass : BuiltinAnnotation

    public sealed interface OnParameter : BuiltinAnnotation

    public sealed interface OnField : BuiltinAnnotation

    /**
     * Represents `javax.inject.Inject`.
     */
    public object Inject :
        OnConstructor, Target.OnConstructor<Inject>,
        OnMethod, Target.OnMethod<Inject>,
        OnField, Target.OnField<Inject> {
        override val modelClass: Class<Inject> get() = Inject::class.java
    }

    /**
     * Represents `javax.inject.Scope`.
     */
    public object Scope : OnAnnotationClass, Target.OnAnnotationClass<Scope> {
        override val modelClass: Class<Scope> get() = Scope::class.java
    }

    /**
     * Represents `javax.inject.Qualifier`.
     */
    public object Qualifier : OnAnnotationClass, Target.OnAnnotationClass<Qualifier> {
        override val modelClass: Class<Qualifier> get() = Qualifier::class.java
    }

    /**
     * Models `com.yandex.yatagan.Module` annotation.
     */
    public interface Module : OnClass {
        public val includes: List<Type>
        public val subcomponents: List<Type>

        public companion object : Target.OnClass<Module> {
            override val modelClass: Class<Module> get() = Module::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.Binds` annotation.
     */
    public object Binds : OnMethod, Target.OnMethod<Binds> {
        override val modelClass: Class<Binds> get() = Binds::class.java
    }

    /**
     * Models `com.yandex.yatagan.Provides` annotation.
     */
    public interface Provides : OnMethod {
        public val conditionals: List<Conditional>

        public companion object : Target.OnMethod<Provides> {
            override val modelClass: Class<Provides> get() = Provides::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.Component` annotation.
     */
    public interface Component : OnClass {
        public val isRoot: Boolean
        public val modules: List<Type>
        public val dependencies: List<Type>
        public val variant: List<Type>
        public val multiThreadAccess: Boolean

        /**
         * Models `com.yandex.yatagan.Component.Builder` annotation.
         */
        public object Builder : OnClass, Target.OnClass<Builder> {
            override val modelClass: Class<Builder> get() = Builder::class.java
        }

        public companion object : Target.OnClass<Component> {
            override val modelClass: Class<Component> get() = Component::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.BindsInstance` annotation.
     */
    public object BindsInstance :
        OnMethod, Target.OnMethod<BindsInstance>,
        OnParameter, Target.OnParameter<BindsInstance> {
        override val modelClass: Class<BindsInstance> get() = BindsInstance::class.java
    }

    public sealed interface ConditionFamily : OnClassRepeatable {
        /**
         * Represents `com.yandex.yatagan.Condition` annotation.
         */
        public interface One : ConditionFamily {
            public val target: Type
            public val condition: String
        }

        /**
         * Represents `com.yandex.yatagan.AnyCondition` annotation.
         */
        public interface Any : ConditionFamily {
            public val conditions: List<One>
        }

        public companion object : Target.OnClassRepeatable<ConditionFamily> {
            override val modelClass: Class<ConditionFamily> get() = ConditionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.Conditional` annotation.
     */
    public interface Conditional : OnClassRepeatable {
        public val featureTypes: List<Type>
        public val onlyIn: List<Type>

        public companion object : Target.OnClassRepeatable<Conditional> {
            override val modelClass: Class<Conditional> get() = Conditional::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.ComponentVariantDimension` annotation.
     */
    public object ComponentVariantDimension : OnClass, Target.OnClass<ComponentVariantDimension> {
        override val modelClass: Class<ComponentVariantDimension> get() = ComponentVariantDimension::class.java
    }

    /**
     * Represents `com.yandex.yatagan.ComponentFlavor` annotation.
     */
    public interface ComponentFlavor : OnClass {
        public val dimension: Type

        public companion object : Target.OnClass<ComponentFlavor> {
            override val modelClass: Class<ComponentFlavor> get() = ComponentFlavor::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.AssistedInject` annotation.
     */
    public object AssistedInject : OnConstructor, Target.OnConstructor<AssistedInject> {
        override val modelClass: Class<AssistedInject> get() = AssistedInject::class.java
    }

    /**
     * `com.yandex.yatagan.Assisted` annotation model.
     */
    public interface Assisted : OnParameter {
        public val value: String

        public companion object : Target.OnParameter<Assisted> {
            override val modelClass: Class<Assisted> get() = Assisted::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.AssistedFactory` annotation.
     */
    public object AssistedFactory : OnClass, Target.OnClass<AssistedFactory> {
        override val modelClass: Class<AssistedFactory> get() = AssistedFactory::class.java
    }

    /**
     * Represents `com.yandex.yatagan.Multibinds` annotation.
     */
    public object Multibinds : OnMethod, Target.OnMethod<Multibinds> {
        override val modelClass: Class<Multibinds> get() = Multibinds::class.java
    }

    public sealed interface IntoCollectionFamily : OnMethodRepeatable {
        public val flatten: Boolean

        /**
         * Models `com.yandex.yatagan.IntoList` annotations.
         */
        public interface IntoList : IntoCollectionFamily

        /**
         * Models `com.yandex.yatagan.IntoSet` annotations.
         */
        public interface IntoSet : IntoCollectionFamily

        public companion object : Target.OnMethodRepeatable<IntoCollectionFamily> {
            override val modelClass: Class<IntoCollectionFamily> get() = IntoCollectionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.IntoMap` annotation.
     */
    public object IntoMap : OnMethod, Target.OnMethod<IntoMap> {
        override val modelClass: Class<IntoMap> get() = IntoMap::class.java

        /**
         * Represents `com.yandex.yatagan.IntoMap.Key` annotation.
         */
        public object Key : OnAnnotationClass, Target.OnAnnotationClass<Key> {
            override val modelClass: Class<Key> get() = Key::class.java
        }
    }
}
