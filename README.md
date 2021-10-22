# Dagger Lite
Like Dagger2 only lighter (supports only specific subset of original API). Supports dynamic graphs with feature-scopes
(merged functionality of the former Dagger Whetstone).

# Roadmap

## MVP
Solution _only works with valid Dagger code_ for fast and flexible prototyping.
It is undefined behavior when the code is invalid: crashes, incorrect code generation, etc. may occur.
Such behaviour should later be fixed with validation steps.

### Core Dagger API

- [x] `@Component` - only interface allowed
    - [x] `@Component.Factory` - only interface allowed
- [x] `@Subcomponent` - implemented as `@Component(isRoot=false)`
- [x] `@Component(dependencies=...)`
- [x] `@Module`
    - [x] `@Binds` - no scope allowed
    - [x] `@Provides`
    - [x] kotlin objects support
    - [ ] companion object support
    - [x] module with instance support

Explicitly not supported API
- `@Component.Builder`/`@Subcomponent.Builder`
- `@BindsOptionalOf`
- `@IntoMap`, `@MapKey`, ..

### Whetstone API

- [ ] `@BindIn` is replaced by: (???)
  - [ ] `@UnderFeature` for features
  - [ ] `@Module(subscribers=...)` for event subscription

## Full solution

- [ ] Validation and full-blown error messages.
- [ ] Whetstone Validation
- [ ] Multi-bindings
  - [ ] `@IntoSet`
  - [ ] `@ElementsIntoSet`
  - [ ] `@Multibinds`
- [ ] `@Reusable`