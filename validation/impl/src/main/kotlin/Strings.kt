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
        fun `binds param type is incompatible with return type`(param: Any, returnType: Any) =
            "@Binds parameter $param is not compatible with its return type $returnType"

        @Covered
        fun `incompatible condition scope`(aCondition: Any, bCondition: Any, a: Any, b: Any) =
            "Condition $aCondition is not always true, given $bCondition is true,\n" +
                    "$Indent=> `$a` can not be injected into `$b` without `Optional<>` wrapper."


        fun `invalid builder setter return type`(creatorType: Any) =
            "Setter method in component creator must return either `void` or creator type itself ($creatorType)"

        fun `component creator must be an interface`() =
            "Component creator declaration must be an `interface`"

        fun `missing component creating method`() =
            "Component creator is missing a creating method - a method which returns the component interface"

        fun `invalid method in component creator`(method: Any) =
            "Unexpected/unrecognized method \n$Indent`$method`\n for component creator interface"

        fun `missing component dependency`(missing: Any) =
            "Declared dependency $missing is missing"

        fun `unneeded component dependency`(extra: Any) =
            "Extra/unneeded type (dependency) $extra is present"

        fun `missing module`(missing: Any) =
            "Declared module $missing requires object instance and it is not provided"

        fun `unneeded module`(extra: Any) =
            "Extra/unneeded module instance $extra is present"


        fun `declaration is not annotated with @Component`() =
            "Type declaration is used as a component yet not annotated with `@Component`"

        fun `component must be an interface`() =
            "Component declaration must be an `interface`"

        fun `missing component creator - non-root`() =
            "Non-root component declaration must include creator declaration"

        fun `missing component creator - dependencies`() =
            "Component declares dependencies, yet no creator declaration is present"

        fun `missing component creator - modules`() =
            "Component includes non-trivially constructable modules that require object instance, " +
                    "yet no creator declaration is present"


        fun `no conditions on feature`() =
            "Feature declaration has no `@Condition`-family annotations on it."


        fun `declaration is not annotated with @ComponentVariantDimension`() =
            "Type declaration is used as a component variant dimension, " +
                    "yet not annotated with @ComponentVariantDimension"

        fun `missing component variant dimension`() =
            "Component variant dimension is missing"


        fun `declaration is not annotated with @ComponentFlavor`() =
            "Type declaration is used as a component flavor, yet not annotated with @ComponentFlavor"


        fun `declaration is not annotated with @Module`() =
            "Type declaration is used as a module, yet not annotated with @Module"


        fun `framework type is manually managed`() =
            "Framework types (Lazy, Provider, Optional) can't be manually managed (provided/bound)"


        fun `conflicting flavors for dimension`(dimension: Any) =
            "Conflicting flavors for a single dimension $dimension"


        fun `undeclared dimension in variant`(dimension: Any, variant: Any) =
            "No flavor is declared for dimension $dimension in a variant $variant"

        fun `variant matching ambiguity`(one: Any, two: Any) =
            "Variant matching ambiguity: $one vs $two could not be resolved"


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
    }

    object Warnings {
        @Covered
        fun `custom binding shadow @Inject constructor`(target: Any, binding: Any) =
            "`$target` has an inject constructor, yet a custom binding\n$Indent`$binding`\nis used " +
                    "instead. This is usually confusing and error-prone. Please, either tweak an " +
                    "inject constructor/conditionals and remove this binding, or remove inject constructor in " +
                    "favor of this binding."

        fun `exposed dependency of a framework type`(functionName: Any, returnType: Any) =
            "`$functionName` has return type `$returnType` which is a framework type thus it can not be directly " +
                    "introduced to the graph via component dependency - the function will be ignored." +
                    "If you need this to form a binding - change the return type, or use a wrapper type. " +
                    "Otherwise remove the function from the dependency interface entirely."

        fun `non-abstract dependency declaration`() =
            "Component dependency declaration is not abstract. If it is already known how to provide necessary " +
                    "dependencies for the graph, consider using Inject-constructors or a @Module with " +
                    "regular provisions instead."
    }

    object Notes {
        @Covered
        fun `no known way to infer a binding`() =
            "No known way to infer the binding"
    }
}
