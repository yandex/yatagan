package com.yandex.daggerlite.lang

/**
 * Marker interface for all framework (builtin) annotations.
 *
 * These are modeled separately from [Annotated] API, as some implementation may optimize working with _known_
 *  annotation classes and/or handle their names in a specific way.
 */
sealed interface BuiltinAnnotation{
    /**
     * A marker interface for builtin annotation class.
     * All direct sub-interfaces denote a specific annotation target.
     */
    sealed interface Target<T : BuiltinAnnotation> {
        val modelClass: Class<out T>

        sealed interface OnMethod<T : BuiltinAnnotation.OnMethod> : Target<T>
        sealed interface OnMethodRepeatable<T : BuiltinAnnotation.OnMethodRepeatable> : Target<T>

        sealed interface OnConstructor<T : BuiltinAnnotation.OnConstructor> : Target<T>

        sealed interface OnClass<T : BuiltinAnnotation.OnClass> : Target<T>
        sealed interface OnClassRepeatable<T : BuiltinAnnotation.OnClassRepeatable> : Target<T>

        sealed interface OnAnnotationClass<T : BuiltinAnnotation.OnAnnotationClass> : Target<T>

        sealed interface OnParameter<T : BuiltinAnnotation.OnParameter> : Target<T>

        sealed interface OnField<T : BuiltinAnnotation.OnField> : Target<T>
    }

    sealed interface OnMethod : BuiltinAnnotation
    sealed interface OnMethodRepeatable : BuiltinAnnotation

    sealed interface OnConstructor : BuiltinAnnotation

    sealed interface OnClass : BuiltinAnnotation
    sealed interface OnClassRepeatable : BuiltinAnnotation

    sealed interface OnAnnotationClass : BuiltinAnnotation

    sealed interface OnParameter : BuiltinAnnotation

    sealed interface OnField : BuiltinAnnotation

    /**
     * Represents `javax.inject.Inject`.
     */
    object Inject :
        OnConstructor, Target.OnConstructor<Inject>,
        OnMethod, Target.OnMethod<Inject>,
        OnField, Target.OnField<Inject> {
        override val modelClass get() = Inject::class.java
    }

    /**
     * Represents `javax.inject.Scope`.
     */
    object Scope : OnAnnotationClass, Target.OnAnnotationClass<Scope> {
        override val modelClass get() = Scope::class.java
    }

    /**
     * Represents `javax.inject.Qualifier`.
     */
    object Qualifier : OnAnnotationClass, Target.OnAnnotationClass<Qualifier> {
        override val modelClass get() = Qualifier::class.java
    }

    /**
     * Models `com.yandex.daggerlite.Module` annotation.
     */
    interface Module : OnClass {
        val includes: List<Type>
        val subcomponents: List<Type>

        companion object : Target.OnClass<Module> {
            override val modelClass get() = Module::class.java
        }
    }

    /**
     * Models `com.yandex.daggerlite.Binds` annotation.
     */
    object Binds : OnMethod, Target.OnMethod<Binds> {
        override val modelClass get() = Binds::class.java
    }

    /**
     * Models `com.yandex.daggerlite.Provides` annotation.
     */
    interface Provides : OnMethod {
        val conditionals: List<Conditional>

        companion object : Target.OnMethod<Provides> {
            override val modelClass get() = Provides::class.java
        }
    }

    /**
     * Models `com.yandex.daggerlite.Component` annotation.
     */
    interface Component : OnClass {
        val isRoot: Boolean
        val modules: List<Type>
        val dependencies: List<Type>
        val variant: List<Type>
        val multiThreadAccess: Boolean

        /**
         * Models `com.yandex.daggerlite.Component.Builder` annotation.
         */
        object Builder : OnClass, Target.OnClass<Builder> {
            override val modelClass get() = Builder::class.java
        }

        companion object : Target.OnClass<Component> {
            override val modelClass get() = Component::class.java
        }
    }

    /**
     * Models `com.yandex.daggerlite.BindsInstance` annotation.
     */
    object BindsInstance :
        OnMethod, Target.OnMethod<BindsInstance>,
        OnParameter, Target.OnParameter<BindsInstance> {
        override val modelClass get() = BindsInstance::class.java
    }

    sealed interface ConditionFamily : OnClassRepeatable {
        /**
         * Represents `com.yandex.daggerlite.Condition` annotation.
         */
        interface One : ConditionFamily {
            val target: Type
            val condition: String
        }

        /**
         * Represents `com.yandex.daggerlite.AnyCondition` annotation.
         */
        interface Any : ConditionFamily {
            val conditions: List<One>
        }

        companion object : Target.OnClassRepeatable<ConditionFamily> {
            override val modelClass get() = ConditionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.daggerlite.Conditional` annotation.
     */
    interface Conditional : OnClassRepeatable {
        val featureTypes: List<Type>
        val onlyIn: List<Type>

        companion object : Target.OnClassRepeatable<Conditional> {
            override val modelClass get() = Conditional::class.java
        }
    }

    /**
     * Represents `com.yandex.daggerlite.ComponentVariantDimension` annotation.
     */
    object ComponentVariantDimension : OnClass, Target.OnClass<ComponentVariantDimension> {
        override val modelClass get() = ComponentVariantDimension::class.java
    }

    /**
     * Represents `com.yandex.daggerlite.ComponentFlavor` annotation.
     */
    interface ComponentFlavor : OnClass {
        val dimension: Type

        companion object : Target.OnClass<ComponentFlavor> {
            override val modelClass get() = ComponentFlavor::class.java
        }
    }

    /**
     * Represents `com.yandex.daggerlite.AssistedInject` annotation.
     */
    object AssistedInject : OnConstructor, Target.OnConstructor<AssistedInject> {
        override val modelClass get() = AssistedInject::class.java
    }

    /**
     * `com.yandex.daggerlite.Assisted` annotation model.
     */
    interface Assisted : OnParameter {
        val value: String

        companion object : Target.OnParameter<Assisted> {
            override val modelClass get() = Assisted::class.java
        }
    }

    /**
     * Represents `com.yandex.daggerlite.AssistedFactory` annotation.
     */
    object AssistedFactory : OnClass, Target.OnClass<AssistedFactory> {
        override val modelClass get() = AssistedFactory::class.java
    }

    /**
     * Represents `com.yandex.daggerlite.Multibinds` annotation.
     */
    object Multibinds : OnMethod, Target.OnMethod<Multibinds> {
        override val modelClass get() = Multibinds::class.java
    }

    sealed interface IntoCollectionFamily : OnMethodRepeatable {
        val flatten: Boolean

        /**
         * Models `com.yandex.daggerlite.IntoList` annotations.
         */
        interface IntoList : IntoCollectionFamily

        /**
         * Models `com.yandex.daggerlite.IntoSet` annotations.
         */
        interface IntoSet : IntoCollectionFamily

        companion object : Target.OnMethodRepeatable<IntoCollectionFamily> {
            override val modelClass get() = IntoCollectionFamily::class.java
        }
    }

    /**
     * Represents `com.yandex.daggerlite.IntoMap` annotation.
     */
    object IntoMap : OnMethod, Target.OnMethod<IntoMap> {
        override val modelClass get() = IntoMap::class.java

        /**
         * Represents `com.yandex.daggerlite.IntoMap.Key` annotation.
         */
        object Key : OnAnnotationClass, Target.OnAnnotationClass<Key> {
            override val modelClass get() = Key::class.java
        }
    }
}
