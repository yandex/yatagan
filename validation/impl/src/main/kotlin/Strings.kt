package com.yandex.daggerlite.validation.impl

@Suppress("FunctionName")
object Strings {
    private fun red(string: String): String = string.lines().joinToString(separator = "\n") {
        "\u001b[31m$it\u001b[0m"
    }

    private fun cyan(string: String): String = "\u001b[36m$string\u001b[0m"

    private const val Indent = "    "


    fun formatMessage(
        message: String,
        encounterPaths: Collection<List<Any>>,
        notes: Collection<String> = emptyList(),
    ): String {
        return buildString {
            appendLine(red(message))
            if (notes.size == 1) {
                append(cyan("• NOTE: "))
                appendLine(notes.first())
            } else {
                notes.forEachIndexed { index, note ->
                    append(cyan("• NOTE #${index + 1}:")).append(' ')
                    appendLine(note)
                }
            }
            appendLine("Encountered in:")
            encounterPaths.joinTo(this, separator = "\n") { path ->
                val pathElement = path.joinToString(separator = " ⟶ ") {
                    cyan(it.toString())
                }
                "$Indent$pathElement"
            }
        }
    }

    /**
     * Just a marker for a message string function, that the corresponding case is covered with test(s).
     */
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Covered

    object Errors {
        @Covered
        fun `missing binding`(`for`: Any) =
            "Missing binding for $`for`"

        @Covered
        fun `no matching scope for binding`(binding: Any, scope: Any?) =
            "No components in the hierarchy match binding -> \n$Indent`$binding`\n -> with scope $scope"

        @Covered
        fun `invalid flattening multibinding`(insteadOf: Any) =
            "Flattening multi-binding must return `Collection` or any of its subtypes instead of `$insteadOf`"

        @Covered
        fun `binding must not return void`() =
            "Binding method must not return `void`"

        @Covered
        fun `binds must be abstract`() =
            "@Binds annotated method must be abstract"

        @Covered
        fun `provides must not be abstract`() =
            "@Provides annotated method must not be abstract (must have a body)"

        @Covered
        fun `self-dependent binding`() =
            "Binding depends on itself"

        @Covered
        fun `binds param type is incompatible with return type`(param: Any, returnType: Any) =
            "@Binds parameter $param is not compatible with its return type $returnType"

        @Covered
        fun `incompatible condition scope`(aCondition: Any, bCondition: Any, a: Any, b: Any) =
            "Condition $aCondition is not always true, given $bCondition is true,\n" +
                    "$Indent=> `$a` can not be injected into `$b` without `Optional<>` wrapper."

        @Covered
        fun `incompatible condition scope for entry-point`(aCondition: Any, bCondition: Any,
                                                           binding: Any, component: Any) =
            "Entry-point condition $aCondition is not always true, given component's condition $bCondition is true,\n" +
                    "$Indent=> `$binding` can not be exposed from `$component` without `Optional<>` wrapper."


        @Covered
        fun `invalid builder setter return type`(creatorType: Any) =
            "Setter method in component creator must return either `void` or creator type itself ($creatorType)"

        @Covered
        fun `component creator must be an interface`() =
            "Component creator declaration must be an `interface`"

        @Covered
        fun `missing component creating method`() =
            "Component creator is missing a creating method - an abstract method which returns the component interface"

        @Covered
        fun `invalid method in component creator`(method: Any) =
            "Unexpected/unrecognized method \n$Indent`$method`\n for component creator interface"

        @Covered
        fun `missing component dependency`(missing: Any) =
            "Declared dependency $missing is missing"

        @Covered
        fun `unneeded component dependency`() =
            "Unrecognized type (component dependency?) is present"

        @Covered
        fun `missing module`(missing: Any) =
            "Declared module $missing requires object instance and it is not provided"

        @Covered
        fun `unneeded module`() =
            "Extra/unneeded module instance is present"


        @Covered
        fun `non-void injector method return type`() =
            "Injector method should return `void`/`Unit`."


        @Covered
        fun `multiple component creators`() =
            "Multiple component factories detected declared"

        @Covered
        fun `invalid method in component`(method: Any) =
            "Unexpected method \n$Indent`$method`\n in component declaration"

        @Covered
        fun `declaration is not annotated with @Component`() =
            "Type declaration is used as a component yet not annotated with `@Component`"

        @Covered
        fun `component must be an interface`() =
            "Component declaration must be an `interface`"

        @Covered
        fun `missing component creator - non-root`() =
            "Non-root component declaration must include creator declaration"

        @Covered
        fun `missing component creator - dependencies`() =
            "Component declares dependencies, yet no creator declaration is present"

        @Covered
        fun `missing component creator - modules`() =
            "Component includes non-trivially constructable modules that require object instance, " +
                    "yet no creator declaration is present"


        @Covered
        fun `no conditions on feature`() =
            "Feature declaration has no `@Condition`-family annotations on it."


        @Covered
        fun `declaration is not annotated with @ComponentVariantDimension`() =
            "Type declaration is used as a component variant dimension, " +
                    "yet not annotated with @ComponentVariantDimension"

        @Covered
        fun `missing component variant dimension`() =
            "Component variant dimension is missing"


        @Covered
        fun `declaration is not annotated with @ComponentFlavor`() =
            "Type declaration is used as a component flavor, yet not annotated with @ComponentFlavor"


        fun `declaration is not annotated with @Module`() =
            "Type declaration is used as a module, yet not annotated with @Module"


        fun `framework type is manually managed`() =
            "Framework types (Lazy, Provider, Optional) can't be manually managed (provided/bound)"


        @Covered
        fun `conflicting or duplicate flavors for dimension`(dimension: Any) =
            "Duplicate flavors for a single `$dimension`"


        @Covered
        fun `undeclared dimension in variant`(dimension: Any) =
            "No flavor is declared for `$dimension` in a variant"

        fun `variant matching ambiguity`(one: Any, two: Any) =
            "Variant matching ambiguity: `$one` vs `$two` could not be resolved"


        fun `invalid condition`(expression: Any) =
            "Invalid condition expression '$expression'"

        fun `invalid condition - unable to reach boolean`() =
            "Unable to reach boolean result in the given expression"

        fun `invalid condition - missing member`(name: Any, type: Any) =
            "Can not find accessible `$name` member in $type"


        fun `conflicting list declarations`(`for`: Any) =
            "Conflicting list declarations for $`for`"

        fun `conflicting bindings`(`for`: Any) =
            "Multiple bindings for $`for`"

        @Covered
        fun `root component can not be a subcomponent`() =
            "Root component can not be a subcomponent"

        fun `duplicate component scope`(scope: Any) =
            "A single scope `$scope` can not be present on more than one component in a hierarchy"

        fun `component hierarchy loop`() =
            "component hierarchy loop"

        @Covered
        fun `dependency loop`(chain: List<Pair<Any, Any>>) = buildString {
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
        fun `custom binding shadow @Inject constructor`(target: Any, binding: Any) =
            "`$target` has an inject constructor, yet a custom binding\n$Indent`$binding`\nis used " +
                    "instead. This is usually confusing and error-prone. Please, either tweak an " +
                    "inject constructor/conditionals and remove this binding, or remove inject constructor in " +
                    "favor of this binding."

        @Covered
        fun `exposed dependency of a framework type`(function: Any) =
            "function\n$Indent`$function`\nreturns a framework type and such type can not be directly " +
                    "introduced to the graph via component dependency - the function will be ignored. " +
                    "If you need this to form a binding - change the return type, or use a wrapper type. " +
                    "Otherwise remove the function from the dependency interface entirely."

        @Covered
        fun `non-abstract dependency declaration`() =
            "Component dependency declaration is not abstract. If it is already known how to provide necessary " +
                    "dependencies for the graph, consider using Inject-constructors or a @Module with " +
                    "regular provisions instead."

        @Covered
        fun `@BindsInstance on builder method's parameter`() =
            "A parameter of a builder's method is annotated with @BindsInstance, which has no effect. " +
                    "Maybe you meant to annotate the method itself for it to work as a binding?"
    }

    object Notes {
        @Covered
        fun `no known way to infer a binding`() =
            "No known way to infer the binding"

        @Covered
        fun `missing module instance`(module: Any) =
            "Instance of `$module` must be provided"

        @Covered
        fun `conflicting component creator declared`(creator: Any) =
            "Declared $creator"
    }
}
