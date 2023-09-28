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

import com.yandex.yatagan.base.api.Internal

/**
 * Marker interface for all framework (builtin) annotations.
 *
 * These are modeled separately from [Annotated] API, as some implementation may optimize working with _known_
 *  annotation classes and/or handle their names in a specific way.
 */
@Internal
public sealed interface BuiltinAnnotation {
    /**
     * A marker interface for builtin annotation class.
     * All direct sub-interfaces denote a specific annotation target.
     */
    @Internal
    public sealed interface Target<T : BuiltinAnnotation> {
        public val modelClass: Class<out T>

        @Internal
        public sealed interface OnMethod<T : BuiltinAnnotation.OnMethod> : Target<T>

        @Internal
        public sealed interface OnMethodRepeatable<T : BuiltinAnnotation.OnMethodRepeatable> : Target<T>

        @Internal
        public sealed interface OnConstructor<T : BuiltinAnnotation.OnConstructor> : Target<T>

        @Internal
        public sealed interface OnClass<T : BuiltinAnnotation.OnClass> : Target<T>

        @Internal
        public sealed interface OnClassRepeatable<T : BuiltinAnnotation.OnClassRepeatable> : Target<T>

        @Internal
        public sealed interface OnAnnotationClass<T : BuiltinAnnotation.OnAnnotationClass> : Target<T>

        @Internal
        public sealed interface OnParameter<T : BuiltinAnnotation.OnParameter> : Target<T>

        @Internal
        public sealed interface OnField<T : BuiltinAnnotation.OnField> : Target<T>

        @Internal
        public sealed interface CanBeCastedOut<T : BuiltinAnnotation.CanBeCastedOut> : Target<T>
    }

    @Internal
    public sealed interface OnMethod : BuiltinAnnotation

    @Internal
    public sealed interface OnMethodRepeatable : BuiltinAnnotation

    @Internal
    public sealed interface OnConstructor : BuiltinAnnotation

    @Internal
    public sealed interface OnClass : BuiltinAnnotation

    @Internal
    public sealed interface OnClassRepeatable : BuiltinAnnotation

    @Internal
    public sealed interface OnAnnotationClass : BuiltinAnnotation

    @Internal
    public sealed interface OnParameter : BuiltinAnnotation

    @Internal
    public sealed interface OnField : BuiltinAnnotation

    @Internal
    public sealed interface CanBeCastedOut : BuiltinAnnotation

    /**
     * Represents `javax.inject.Inject`.
     */
    @Internal
    public object Inject :
        OnConstructor, Target.OnConstructor<Inject>,
        OnMethod, Target.OnMethod<Inject>,
        OnField, Target.OnField<Inject> {
        override val modelClass: Class<Inject> get() = Inject::class.java
    }

    /**
     * Represents `javax.inject.Scope`.
     */
    @Internal
    public object Scope : OnAnnotationClass, Target.OnAnnotationClass<Scope> {
        override val modelClass: Class<Scope> get() = Scope::class.java
    }

    /**
     * Represents `javax.inject.Qualifier`.
     */
    @Internal
    public object Qualifier : OnAnnotationClass, Target.OnAnnotationClass<Qualifier> {
        override val modelClass: Class<Qualifier> get() = Qualifier::class.java
    }

    /**
     * Models `com.yandex.yatagan.Module` annotation.
     */
    @Internal
    public interface Module : OnClass {
        public val includes: List<Type>
        public val subcomponents: List<Type>

        @Internal
        public companion object : Target.OnClass<Module> {
            override val modelClass: Class<Module> get() = Module::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.Binds` annotation.
     */
    @Internal
    public object Binds : OnMethod, Target.OnMethod<Binds> {
        override val modelClass: Class<Binds> get() = Binds::class.java
    }

    /**
     * Models `com.yandex.yatagan.Provides` annotation.
     */
    @Internal
    public interface Provides : OnMethod {
        public val conditionals: List<Conditional>

        @Internal
        public companion object : Target.OnMethod<Provides> {
            override val modelClass: Class<Provides> get() = Provides::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.Component` annotation.
     */
    @Internal
    public interface Component : OnClass {
        public val isRoot: Boolean
        public val modules: List<Type>
        public val dependencies: List<Type>
        public val variant: List<Type>
        public val multiThreadAccess: Boolean

        /**
         * Models `com.yandex.yatagan.Component.Builder` annotation.
         */
        @Internal
        public object Builder : OnClass, Target.OnClass<Builder> {
            override val modelClass: Class<Builder> get() = Builder::class.java
        }

        @Internal
        public companion object : Target.OnClass<Component> {
            override val modelClass: Class<Component> get() = Component::class.java
        }
    }

    /**
     * Models `com.yandex.yatagan.BindsInstance` annotation.
     */
    @Internal
    public object BindsInstance :
        OnMethod, Target.OnMethod<BindsInstance>,
        OnParameter, Target.OnParameter<BindsInstance> {
        override val modelClass: Class<BindsInstance> get() = BindsInstance::class.java
    }

    @Internal
    public sealed interface ConditionFamily : OnClassRepeatable {
        /**
         * Represents `com.yandex.yatagan.Condition` annotation.
         */
        @Internal
        public interface One : ConditionFamily {
            public val target: Type
            public val condition: String
        }

        /**
         * Represents `com.yandex.yatagan.AnyCondition` annotation.
         */
        @Internal
        public interface Any : ConditionFamily {
            public val conditions: List<One>
        }

        @Internal
        public companion object : Target.OnClassRepeatable<ConditionFamily> {
            override val modelClass: Class<ConditionFamily> get() = ConditionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.ConditionExpression` annotation.
     */
    @Internal
    public interface ConditionExpression : OnClass {
        public val value: String
        public val imports: List<Type>
        public val importAs: List<ImportAs>

        @Internal
        public interface ImportAs {
            public val value: Type
            public val alias: String
        }

        @Internal
        public companion object : Target.OnClass<ConditionExpression> {
            override val modelClass: Class<out ConditionExpression> get() = ConditionExpression::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.Conditional` annotation.
     */
    @Internal
    public interface Conditional : OnClassRepeatable {
        public val featureTypes: List<Type>
        public val onlyIn: List<Type>

        @Internal
        public companion object : Target.OnClassRepeatable<Conditional> {
            override val modelClass: Class<Conditional> get() = Conditional::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.ComponentVariantDimension` annotation.
     */
    @Internal
    public object ComponentVariantDimension : OnClass, Target.OnClass<ComponentVariantDimension> {
        override val modelClass: Class<ComponentVariantDimension> get() = ComponentVariantDimension::class.java
    }

    /**
     * Represents `com.yandex.yatagan.ComponentFlavor` annotation.
     */
    @Internal
    public interface ComponentFlavor : OnClass {
        public val dimension: Type

        @Internal
        public companion object : Target.OnClass<ComponentFlavor> {
            override val modelClass: Class<ComponentFlavor> get() = ComponentFlavor::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.AssistedInject` annotation.
     */
    @Internal
    public object AssistedInject : OnConstructor, Target.OnConstructor<AssistedInject> {
        override val modelClass: Class<AssistedInject> get() = AssistedInject::class.java
    }

    /**
     * `com.yandex.yatagan.Assisted` annotation model.
     */
    @Internal
    public interface Assisted : OnParameter {
        public val value: String

        @Internal
        public companion object : Target.OnParameter<Assisted> {
            override val modelClass: Class<Assisted> get() = Assisted::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.AssistedFactory` annotation.
     */
    @Internal
    public object AssistedFactory : OnClass, Target.OnClass<AssistedFactory> {
        override val modelClass: Class<AssistedFactory> get() = AssistedFactory::class.java
    }

    /**
     * Represents `com.yandex.yatagan.Multibinds` annotation.
     */
    @Internal
    public object Multibinds : OnMethod, Target.OnMethod<Multibinds> {
        override val modelClass: Class<Multibinds> get() = Multibinds::class.java
    }

    @Internal
    public sealed interface IntoCollectionFamily : OnMethodRepeatable {
        public val flatten: Boolean

        /**
         * Models `com.yandex.yatagan.IntoList` annotations.
         */
        @Internal
        public interface IntoList : IntoCollectionFamily

        /**
         * Models `com.yandex.yatagan.IntoSet` annotations.
         */
        @Internal
        public interface IntoSet : IntoCollectionFamily

        @Internal
        public companion object : Target.OnMethodRepeatable<IntoCollectionFamily> {
            override val modelClass: Class<IntoCollectionFamily> get() = IntoCollectionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.yatagan.IntoMap` annotation.
     */
    @Internal
    public object IntoMap : OnMethod, Target.OnMethod<IntoMap> {
        override val modelClass: Class<IntoMap> get() = IntoMap::class.java

        /**
         * Represents `com.yandex.yatagan.IntoMap.Key` annotation.
         */
        @Internal
        public object Key : OnAnnotationClass, Target.OnAnnotationClass<Key> {
            override val modelClass: Class<Key> get() = Key::class.java
        }
    }

    @Internal
    public interface ValueOf : CanBeCastedOut {
        public val value: ConditionExpression

        @Internal
        public companion object : Target.CanBeCastedOut<ValueOf> {
            override val modelClass: Class<ValueOf> get() = ValueOf::class.java
        }
    }
}
