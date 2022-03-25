package com.yandex.daggerlite.validation.impl

object Strings {
    enum class StringColor(val ansiCode: Int) {
        Red(31),
        Yellow(33),
        Cyan(36),
    }

    private fun String.colorize(color: StringColor) = lines().joinToString(separator = "\n") {
        "\u001b[${color.ansiCode}m$it\u001b[0m"
    }

    private const val Indent = "    "

    fun formatMessage(
        message: String,
        color: StringColor = StringColor.Red,
        encounterPaths: Collection<List<Any>>,
        notes: Collection<String> = emptyList(),
    ): String {
        return buildString {
            appendLine(message.colorize(color))
            if (notes.size == 1) {
                append("• NOTE: ".colorize(StringColor.Cyan))
                appendLine(notes.first())
            } else {
                notes.sorted().forEachIndexed { index, note ->
                    append("• NOTE #${index + 1}:".colorize(StringColor.Cyan)).append(' ')
                    appendLine(note)
                }
            }
            appendLine("Encountered in:")
            encounterPaths.asSequence().take(10).map { path ->
                val pathElement = path.joinToString(separator = " -> ") {
                    it.toString().colorize(StringColor.Cyan)
                }
                "$Indent$pathElement"
            }.sorted().joinTo(this, separator = "\n")
        }
    }

    /**
     * Just a marker for a message string function, that the corresponding case is covered with test(s).
     */
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Covered

    object Errors {
        @Covered
        fun missingBinding(`for`: Any) =
            "Missing binding for $`for`"

        @Covered
        fun noMatchingScopeForBinding(binding: Any, scope: Any?) =
            "No components in the hierarchy match binding -> \n$Indent`$binding`\n -> with scope $scope"

        @Covered
        fun invalidFlatteningMultibinding(insteadOf: Any) =
            "Flattening multi-binding must return `Collection` or any of its subtypes instead of `$insteadOf`"

        @Covered
        fun voidBinding() =
            "Binding method must not return `void`"

        @Covered
        fun nonAbstractBinds() =
            "@Binds annotated method must be abstract"

        @Covered
        fun abstractProvides() =
            "@Provides annotated method must not be abstract (must have a body)"

        @Covered
        fun selfDependentBinding() =
            "Binding depends on itself"

        @Covered
        fun inconsistentBinds(param: Any, returnType: Any) =
            "@Binds parameter $param is not compatible with its return type $returnType"

        @Covered
        fun incompatibleCondition(aCondition: Any, bCondition: Any, a: Any, b: Any) =
            "Condition $aCondition is not always true, given $bCondition is true,\n" +
                    "$Indent=> `$a` can not be injected into `$b` without `Optional<>` wrapper."

        @Covered
        fun incompatibleConditionEntyPoint(
            aCondition: Any, bCondition: Any,
            binding: Any, component: Any,
        ) = "Entry-point condition $aCondition is not always true, given component's condition $bCondition is true,\n" +
                    "$Indent=> `$binding` can not be exposed from `$component` without `Optional<>` wrapper."


        @Covered
        fun invalidBuilderSetterReturn(creatorType: Any) =
            "Setter method in component creator must return either `void` or creator type itself ($creatorType)"

        @Covered
        fun nonInterfaceCreator() =
            "Component creator declaration must be an `interface`"

        @Covered
        fun missingCreatingMethod() =
            "Component creator is missing a creating method - an abstract method which returns the component interface"

        @Covered
        fun unknownMethodInCreator(method: Any) =
            "Unexpected/unrecognized method \n$Indent`$method`\n for component creator interface"

        @Covered
        fun missingComponentDependency(missing: Any) =
            "Declared dependency $missing is missing"

        @Covered
        fun extraComponentDependency() =
            "Unrecognized type (component dependency?) is present"

        @Covered
        fun missingModule(missing: Any) =
            "Declared module $missing requires object instance and it is not provided"

        @Covered
        fun extraModule() =
            "Extra/unneeded module instance is present"


        @Covered
        fun invalidInjectorReturn() =
            "Injector method should return `void`/`Unit`."


        @Covered
        fun multipleCreators() =
            "Multiple component factories detected declared"

        @Covered
        fun unknownMethodInComponent(method: Any) =
            "Unexpected method \n$Indent`$method`\n in component declaration"

        @Covered
        fun nonComponent() =
            "Type declaration is used as a component yet not annotated with `@Component`"

        @Covered
        fun nonInterfaceComponent() =
            "Component declaration must be an `interface`"

        @Covered
        fun missingCreatorForNonRoot() =
            "Non-root component declaration must include creator declaration"

        @Covered
        fun missingCreatorForDependencies() =
            "Component declares dependencies, yet no creator declaration is present"

        @Covered
        fun missingCreatorForModules() =
            "Component includes non-trivially constructable modules that require object instance, " +
                    "yet no creator declaration is present"


        @Covered
        fun noConditionsOnFeature() =
            "Feature declaration has no `@Condition`-family annotations on it."


        @Covered
        fun nonComponentVariantDimension() =
            "Type declaration is used as a component variant dimension, " +
                    "yet not annotated with @ComponentVariantDimension"

        @Covered
        fun missingDimension() =
            "Component variant dimension is missing"


        @Covered
        fun nonFlavor() =
            "Type declaration is used as a component flavor, yet not annotated with @ComponentFlavor"


        @Covered
        fun nonModule() =
            "Type declaration is used as a module, yet not annotated with @Module"


        @Covered
        fun manualFrameworkType() =
            "Framework types (Lazy, Provider, Optional) can't be manually managed (provided/bound)"


        @Covered
        fun conflictingOrDuplicateFlavors(dimension: Any) =
            "Duplicate flavors for a single `$dimension`"


        @Covered
        fun undeclaredDimension(dimension: Any) =
            "No flavor is declared for `$dimension` in a variant"

        fun variantMatchingAmbiguity(one: Any, two: Any) =
            "Variant matching ambiguity: `$one` vs `$two` could not be resolved"


        @Covered
        fun invalidCondition(expression: Any) =
            "Invalid condition expression '$expression'"

        @Covered
        fun invalidConditionNoBoolean() =
            "Unable to reach boolean result in the given expression"

        @Covered
        fun invalidConditionMissingMember(name: Any, type: Any) =
            "Can not find accessible `$name` member in $type"


        @Covered
        fun conflictingBindings(`for`: Any) =
            "Conflicting bindings for `$`for``"

        @Covered
        fun rootAsChild() =
            "Root component can not be a subcomponent"

        @Covered
        fun duplicateComponentScope(scope: Any) =
            "A single scope `$scope` can not be present on more than one component in a hierarchy"

        @Covered
        fun componentLoop() =
            "Component hierarchy loop detected"

        @Covered
        fun multiThreadStatusMismatch(parent: Any) =
            "Component declares a multi-threaded requirement, but its parent `$parent` does not. " +
                    "Please, specify the same requirement for the parent."

        @Covered
        fun dependencyLoop(chain: List<Pair<Any, Any>>) = buildString {
            appendLine("Binding dependency loop detected:")
            chain.forEachIndexed { index, (target, binding) ->
                if (index == 0) append("(*) ") else append("    ")
                append('`').append(target).append("` provided by `").append(binding).append("` depends on <-")
                if (index != chain.lastIndex) {
                    appendLine()
                }
            }
            append(" (*)")
        }
    }

    object Warnings {

        @Covered
        fun ignoredDependencyOfFrameworkType(function: Any) =
            "function\n$Indent`$function`\nreturns a framework type and such type can not be directly " +
                    "introduced to the graph via component dependency - the function will be ignored. " +
                    "If you need this to form a binding - change the return type, or use a wrapper type. " +
                    "Otherwise remove the function from the dependency interface entirely."

        @Covered
        fun nonAbstractDependency() =
            "Component dependency declaration is not abstract. If it is already known how to provide necessary " +
                    "dependencies for the graph, consider using Inject-constructors or a @Module with " +
                    "regular provisions instead."

        @Covered
        fun ignoredBindsInstance() =
            "A parameter of a builder's method is annotated with @BindsInstance, which has no effect. " +
                    "Maybe you meant to annotate the method itself for it to work as a binding?"
    }

    object Notes {
        @Covered
        fun unknownBinding() =
            "No known way to infer the binding"

        @Covered
        fun missingModuleInstance(module: Any) =
            "Instance of `$module` must be provided"

        @Covered
        fun conflictingCreator(creator: Any) =
            "Declared $creator"

        @Covered
        fun duplicateBinding(binding: Any) =
            "Conflicting binding: `$binding`"

        @Covered
        fun duplicateScopeComponent(component: Any) =
            "In component `$component`"

        @Covered
        fun nestedFrameworkType(target: Any) =
            "`$target` can't be requested in any way (lazy/provider/optional) but *directly*"

        fun subcomponentFactoryInjectionHint(factory: Any, component: Any, owner: Any) =
            "`$factory` is a factory for `$component`, ensure that this component is specified " +
                    "via `@Module(subcomponents=..)` and that module is included into `$owner`"
    }

    object Bindings {
        fun componentInstance(component: Any) =
            "[intrinsic] component instance `$component`"

        fun componentDependencyInstance(dependency: Any) =
            "[intrinsic] component dependency instance `$dependency`"

        fun instance(origin: Any) =
            "[intrinsic] pre-provided instance via `$origin`"

        fun multibinding(elementType: Any, contributions: Iterable<Any>) = buildString {
            append("[intrinsic] multi-bound `$elementType` list")
            appendLine(":")
            contributions.forEach {
                append(Indent).appendLine(it)
            }
        }

        fun subcomponentFactory(factory: Any) =
            "[intrinsic] subcomponent creator (factory/builder) `$factory`"

        fun componentDependencyEntryPoint(entryPoint: Any) =
            "[intrinsic] provision from dependency `$entryPoint`"
    }
}
