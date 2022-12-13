# Yatagan tutorial

As Yatagan is based on Dagger API, and can be migrated from it with no architectural changes,
it can be used and organised very much like Dagger.

So here we'll only talk of how to use things, that are not inherited from Dagger.

# Create components

Component and component builder implementations are created using `Yatagan.create()` or `Yatagan.builder()` 
invocations respectively.

# Conditions

Let's dive into how to use Yatagan's native support for runtime conditions.

## The problem

Suppose we want to provide a class `MyClass`, but not directly, yet under a condition, that is evaluated at runtime.
The condition can be queried from class `Features`. To represent a conditional presence of `MyClass` we will be using
`java.util.Optional`, as it's designed right for such purpose.

Consider the following Dagger code:
```kotlin
class MyClass @Inject constructor(/* ... */)

@Module
class MyModule {
    @Provides 
    fun provideOptionalFunctionality(
        features: Features,
        provider: Provider<MyClass>,
    ): Optional<MyClass> {
        return if (features.myFeature.isEnabled) {
            Optional.of(provider.get())
        } else Optional.empty()
    }
}
```
Let's spot the immediate downside to this: 
I can still inject `MyClass` directly in the code, as it's still freely available in the graph. 
This can very much happen if someone forgets or even doesn't know that the class is available only under a feature.

Let's try another approach. Let's, in fact, remove the "direct" binding for `MyClass` and construct it on the spot in
our "optional" binding, like this:
```kotlin
@Module
class MyModule {
    @Provides 
    fun provideOptionalFunctionality(
        features: Features,
        myClassDep1: Provider<Dep1>, /* ... , */ myClassDepN: Provider<DepN>,
    ): Optional<MyClass> {
        return if (features.myFeature.isEnabled) {
            Optional.of(MyClass(myClassDep1.get(), /* ... , */ myClassDepN.get()))
        } else Optional.empty()
    }
}
```
The no one will be able to inject `MyClass` without first consulting the condition. 
However, it's also probably not what we want - everyone has to inject `Optional<MyClass>`, 
even classes under **the same or more strict condition**. Also, writing such code with extensive `Provider` usage may
lead Dagger to generate suboptimal code, as to be able to construct Lazy/Provider objects it has to change its
code generation strategy in some cases 
(may not be the case in versions to come, if Dagger team decides to change something).

There are likely other ways to tackle the `Optional<T>` bindings problem. 
However, I found them unsafe/verbose/suboptimal.

## How Yatagan solves it

Yatagan introduces a `@Condition` annotation (in fact, there are several related annotations like that,
but we'll refer to them all as `@Condition` unless specified specifically otherwise). 
This annotation can be used to _declare_ a _feature_ - a conditional expression.

To use declared _features_, Yatagan provides `@Conditional` marker - there one can specify under what _feature_ the
marked binding is.

Let's see this in practice in terms of the example above:
```kotlin
@Condition(Features::class, "getMyFeature.isEnabled")
annotation class MyFeature

@Conditional(MyFeature::class)
class MyClass @Inject constructor(/* ... */)
```
And that's it, Yatagan will now be aware, that `MyClass` is bound under condition. 
If I try to use it without a `com.yandex.yatagan.Optional` wrapper, 
then it'll get an error message mentioning conditions.
However, I can depend on `MyClass` _directly_ from bindings, that are put under the same or more strict condition.
Yatagan framework will validate all conditional dependencies and prove that no violation is present.

### More complex "features"

We've seen a trivial _feature_ declaration - just one condition. In fact, Yatagan supports writing a boolean expression
of arbitrary complexity in a form of CNF (Conjunctive Normal Form: `(a1 || a2 || ..) && (b1 || b2 || ..) && ...`).

For example, to express 
`(Features.isEnabledA || Features.isEnabledB) && Features.isEnabledC && Features.isEnabledD`
one would write using `@Condition` and `@AnyCondition` annotations:
```kotlin
@AnyCondition(
    Condition(Features::class, "isEnabledA"),
    Condition(Features::class, "isEnabledB"),
)
@Condition(Features::class, "isEnabledC")
@Condition(Features::class, "isEnabledD")
annotation class MyFeature
```
Every condition annotation declared directly on the feature is &&-ed with each other in order of declaration.

### How "features" are evaluated

Yatagan evaluates each unique `@Condition` only once per component hierarchy to avoid inconsistencies 
if conditions can be evaluated to different values when queried multiple times. 
The value of each literal is cached and reused if needed.

The "condition provider"-class, that is specified as the first argument to `@Condition` is queried from the graph
as per usual dependency rules. If a class is used as condition provider in 
multiple conditions across component hierarchy branch,
then it's queried and cached in the highest (closest to root) component. 
So, if it's not available there for some reason - the missing binding will be reported.

There's a specific case when the condition path (the string argument for `@Condition`) leads to a value,
that is accessible from _static context_. In this case, Yatagan doesn't try to inject the provider class,
instead it indeed queries the condition from static context,
and it may do so _upon component construction_ if it decides so.
For non-static conditions the time of evaluation is strictly the first construction of a conditional binding with
it in its condition expression (as if per usual "lazy" rules).

# Variants

## The problem

Consider we have a multiple _variations_ of the logically same component. 
For example, an application component for phones or tablets, a screen component for main page or settings, etc.
They share a bulk of dependencies, maybe even have a common superinterface.
We want to make one binding present in one _flavor_ of component, and absent in the others.

This can be achieved with Dagger's `@BindsOptionalOf`:
```kotlin
@Module interface ScreenModule {
    // Common module declares, that a binding can be optional
    @BindsOptionalOf fun maybeOptionalApi(): Api 
}

@Module interface SettingsScreenModule {  // included into settings screen component
    @Binds fun settingsApi(): Api
}
@Module interface MainScreenModule {  // included into main screen component
    // no Api binding
}
```

## How Yatagan provides more flexibility to the solution

Variant API is a logical successor of Dagger's `@BindsOptionalOf`.

If you are familiar with Android's flavors and dimensions, this is very similar to that.
One can explicitly declare a component dimension and its flavors, like this:
```kotlin
@ComponentVariantDimension annotation class Product {
  @ComponentFlavor(dimension = Product::class) annotation class FooApp
  @ComponentFlavor(dimension = Product::class) annotation class BarApp
}

@ComponentVariantDimension annotation class Device {
  @ComponentFlavor(dimension = Device::class) annotation class Tablet
  @ComponentFlavor(dimension = Device::class) annotation class Phone
  @ComponentFlavor(dimension = Device::class) annotation class Watch
}
```
Then, one can declare components with the required _variants_:
```kotlin
@Component(variant = [Product.FooApp::class, Device.Tablet], /*..*/)
interface FooAppTablet { /*..*/ }

@Component(variant = [Product.BarApp::class, Device.Phone], /*..*/)
interface BarAppPhone { /*..*/ }

// .. All required variants - combinations of flavors
```
Variants are also merged (inherited) from parent components.

Now let's see what we can do with those flavors.
`onlyIn` allows us to specify, where the binding is present, and where it is absent.
Multiple directives allows us to specify a condition, specific for a set of flavors.
```kotlin
// Simple runtime condition - entity is accessible in every component under FeatureA:
@Conditional(FeatureA::class)
class UnderFeatureA @Inject constructor()

// Simple compile-time filter - entity is accessible only in components, that declare Device.Watch flavor:
@Conditional(onlyIn = [Device.Watch::class])
class WatchSpecific @Inject constructor()

// Entity is accessible only in components that declare `Device.Phone` in their [variant][Component.variant].
// Inaccessible anywhere else:
@Conditional(FeatureA::class, onlyIn = [Device.Phone::class])
class PhoneSpecificUnderFeatureA @Inject constructor()

// More complex example with multiple conditionals:
@Conditional(FeatureA::class, FeatureB::class, onlyIn = [
  Product.FooApp::class,
  Device.Phone::class, Device.Tablet::class,
])  // accessible in FooApp on phones and tablets (but not on watches) under FeatureA && FeatureB.
@Conditional(FeatureC::class, onlyIn = [
  Product.BarApp::class
])  // accessible in BarApp (in all form-factors) under FeatureC.
class Complex @Inject constructor()
```
Such approach is only suited for some app architectures, nevertheless it's quite useful if applicable. 