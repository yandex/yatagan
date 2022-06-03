package com.yandex.daggerlite.testing.generation

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.yandex.daggerlite.testing.generation.GenerationParams.BindingType
import com.yandex.daggerlite.testing.generation.GenerationParams.DependencyKind
import com.yandex.daggerlite.testing.generation.GenerationParams.ProvisionScopes
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Generates a valid Dagger-Lite graph(s) by given parameters.
 *
 * @param params various aspects that constrain and define the resulting graph.
 * @param sourceDir target sources directory, where to place generated files.
 */
@ExperimentalGenerationApi
fun generate(
    params: GenerationParams,
    sourceDir: File,
    testMethodName: String = "main",
) {
    val rng = Random(params.seed)
    var id = 0L  // This is global entity id, that is used to ensure entities' names are globally unique.

    // 1. Build logical scopes tree.

    val scopes = Forest<LogicalScope>()
    // Create root scopes.
    repeat(params.totalRootScopes) { scopes.addTree(LogicalScope(id = id++)) }
    // Create children while walking.
    scopes.walk { scopeNode ->
        if (scopeNode.depth >= params.componentTreeMaxDepth) {
            // No more children, max depth reached.
            return@walk
        }
        val maxChildren = params.maxChildrenPerScope
            .div(params.maxChildrenPerScopeDepthDecay * scopeNode.depth - 1)
            .roundToInt()
        repeat(rng.nextInt(0..maxChildren)) {
            scopeNode.addChild(LogicalScope(id = id++))
        }
    }

    // 2. Populate logical scopes with nodes.

    val allScopes = buildList { scopes.walk(::add) }
    repeat(params.totalGraphNodesCount) {
        // All available nodes are evenly distributed across all the scopes.
        val scope = allScopes.random(rng)
        val node = LogicalNode(scope = scope, id = id++)
        scope.value.nodes.add(node)
    }

    // 3. Generate dependencies between nodes according to scopes.

    fun randomNode(inside: Tree<LogicalScope>): LogicalNode {
        val dependencyScopeIndex = rng.nextInt(0 until inside.depth)
        val dependencyScope = inside.pathToRoot().drop(dependencyScopeIndex).first()
        return dependencyScope.value.nodes.random(rng)
    }

    fun randomComponentDependency(inside: Tree<LogicalScope>): ComponentDependency {
        val dependencyScopeIndex = rng.nextInt(0 until inside.depth)
        val dependencyScope = inside.pathToRoot().drop(dependencyScopeIndex).first()
        val deps = dependencyScope.value.componentDependencies
        return if (deps.isEmpty()) {
            ComponentDependency(clid = id++, node = null).also(deps::add)
        } else deps.random(rng)
    }

    fun randomProvisionDependencies(inside: Tree<LogicalScope>, except: LogicalNode) = buildMap {
        for (i in 1..rng.nextInt(0..params.maxProvisionDependencies)) {
            var dependency: LogicalNode
            do {
                dependency = randomNode(inside)
            } while (dependency == except)
            put(dependency, params.provisionDependencyKind.roll(rng))
        }
    }

    for (scopeTreeRoot in scopes.trees) {
        scopeTreeRoot.walk { scopeTree ->
            for (node: LogicalNode in scopeTree.value.nodes) {
                // Choose a binding for this node.
                // TODO: support params.maxBindingsPerNode
                val binding = when (params.bindings.roll(rng)) {
                    BindingType.Inject -> Binding.Inject(
                        randomProvisionDependencies(inside = scopeTree, except = node), node)
                    BindingType.Provision -> Binding.Provision(
                        randomProvisionDependencies(inside = scopeTree, except = node), node)
                    BindingType.Alias -> Binding.Alias(randomNode(inside = scopeTree), node)
                    BindingType.Instance -> Binding.Instance(node)
                    BindingType.ComponentDependency -> {
                        // force create new model for the `node`
                        val model = ComponentDependency(id++, node)
                        scopeTree.value.componentDependencies += model
                        Binding.ComponentDependencyInstance(node)
                    }
                    BindingType.ComponentDependencyEntryPoint -> {
                        val model = randomComponentDependency(inside = scopeTree)
                        Binding.ComponentDependencyEntryPoint(target = node).also(model.entryPoints::add)
                    }
                }
                node.bindings += binding
            }
        }
    }

    // 4. Generate components tree.

    val components = Forest<Component>()
    for (rootId in 1..params.totalRootCount) {
        // Choose scope tree
        val rootScope = scopes.trees.random(rng)
        val root = components.addTree(Component(scope = rootScope, id = id++))
        // Create child components while walking.
        root.walk { component ->
            if (component.depth == params.componentTreeMaxDepth) {
                // desired depth is reached
                return@walk
            }
            // TODO: employ decay here.
            val childrenCount = rng.nextInt(0..params.maxChildrenPerComponent)
            val childScopes = component.value.scope.children
            if (childScopes.isNotEmpty()) {
                for (i in 0 until childrenCount) {
                    // TODO: make component hierarchies interlink by sharing children.
                    component.addChild(Component(scope = childScopes.random(rng), id = id++))
                }
            }
        }
    }
    components.walk { componentTree ->
        val component by componentTree::value
        fun Tree<Component>.resolveBinding(node: LogicalNode): Binding {
            val componentWithNodeScope = checkNotNull(pathToRoot().find { it.value.scope == node.scope })
            return componentWithNodeScope.value.localBindings.getOrPut(node) { node.bindings.random(rng) }
        }

        fun Tree<Component>.updateBinding(node: LogicalNode, newBinding: Binding) {
            val componentWithNodeScope = checkNotNull(pathToRoot().find { it.value.scope == node.scope })
            node.bindings += newBinding
            componentWithNodeScope.value.localBindings[node] = newBinding
        }
        // Add necessary quantity of entry-points
        repeat(rng.nextInt(1..params.maxEntryPointsPerComponent)) {
            val node = randomNode(inside = component.scope)
            component.entryPoints[node] = params.provisionDependencyKind.roll(rng)
        }
        // Add necessary quantity of member-injectors
        repeat(rng.nextInt(0..params.maxMemberInjectorsPerComponent)) {
            val members = (0..rng.nextInt(0..params.maxMembersPerInjectee))
                .map { randomNode(inside = component.scope) }
            component.memberInjectors += MemberInjector(id++, members)
        }
        run {
            // Walk the graph, choosing bindings
            val queue = ArrayDeque<LogicalNode>()
            val seen = hashSetOf<LogicalNode>()
            queue += component.entryPoints.keys
            for (memberInjector in component.memberInjectors) {
                queue += memberInjector.members
            }
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (!seen.add(node)) continue
                val binding = componentTree.resolveBinding(node)
                for ((depNode, _) in binding.dependencies) {
                    queue += depNode
                }
            }
        }
        run {
            fun Binding.withoutDependencies(nodes: Collection<LogicalNode>): Binding {
                fun replaceWithLazy(): Map<LogicalNode, DependencyKind> {
                    return buildMap {
                        val nodesSet = nodes.toSet()
                        for ((node, kind) in dependencies) {
                            put(node, if (node in nodesSet) DependencyKind.Lazy else kind)
                        }
                    }
                }
                return when (this) {
                    is Binding.Inject -> apply {
                        // Mutate current binding, as multiple Inject bindings are not supported.
                        dependencies = replaceWithLazy()
                    }
                    is Binding.Provision -> Binding.Provision(replaceWithLazy(), target)
                    is Binding.Alias -> {
                        // Can't replace alias, create a dummy provision
                        Binding.Provision(emptyMap(), target)
                    }
                    is Binding.ComponentDependencyInstance,
                    is Binding.ComponentDependencyEntryPoint,
                    is Binding.Instance,
                    -> this  // no dependencies, no need to clone
                }
            }

            // Break the loops
            val markedGray = hashSetOf<LogicalNode>()
            val markedBlack = hashSetOf<LogicalNode>()
            val stack = arrayListOf<LogicalNode>()

            stack += component.entryPoints.keys
            for (memberInjector in component.memberInjectors) {
                stack += memberInjector.members
            }

            while (stack.isNotEmpty()) {
                when (val node = stack.last()) {
                    in markedBlack -> {
                        stack.removeLast()
                    }
                    in markedGray -> {
                        stack.removeLast()
                        markedBlack += node
                        markedGray -= node
                    }
                    else -> {
                        markedGray += node
                        var binding = componentTree.resolveBinding(node)
                        // Check if this binding introduces a loop into the graph
                        val formsLoopBy = binding.dependencies.mapNotNull { (depNode, depKind) ->
                            depNode.takeIf { depKind == DependencyKind.Direct && depNode in markedGray }
                        }
                        if (formsLoopBy.isNotEmpty()) {
                            // Need to break the loop
                            binding = binding.withoutDependencies(formsLoopBy)
                            componentTree.updateBinding(node, binding)
                        }
                        for ((depNode, depKind) in binding.dependencies) {
                            if (depKind == DependencyKind.Direct) {
                                stack += depNode
                            }
                        }
                    }
                }
            }
        }
    }
    components.walk {
        // Own the bindings
        for (binding in it.value.localBindings.values) {
            binding.usedIn += it.value
        }
    }

    // 4.1 Purge unused bindings, and then, nodes
    scopes.walk { scope ->
        for (componentDependency in scope.value.componentDependencies) {
            componentDependency.entryPoints.removeIf { it.usedIn.isEmpty() }
        }
        for (node in scope.value.nodes) {
            node.bindings.removeIf { it.usedIn.isEmpty() }
        }
        scope.value.nodes.removeIf { it.bindings.isEmpty() }
    }
    var totalNodes = 0
    scopes.walk { scope ->
        totalNodes += scope.value.nodes.size
    }
    println("Total nodes in graph: $totalNodes")

    // 5. Build modules
    val allModules = arrayListOf<Tree<Module>>()
    components.walk { component ->
        // TODO: Try to group common bindings into common modules
        // TODO: Make an actual hierarchy of modules.
        val module = component.value.modules.addTree(Module(id++))
        allModules += module
        for (child in component.children)
            module.value.subcomponents += child.value
        for (binding in component.value.localBindings.values) {
            when (binding) {
                is Binding.Alias, is Binding.Provision -> {
                    module.value.bindings += binding
                }
                else -> Unit // do nothing
            }
        }
    }

    // 6. We generate code with:
    //  - Every node, to back its typeName.
    //  - ComponentDependencyModel
    //  - Module
    //  - Scope
    //  - Component

    val classes = mutableMapOf<ClassName, TypeSpec.Builder>()
    fun classFor(name: ClassName) = classes.getOrPut(name) { TypeSpec.classBuilder(name) }
    fun interfaceFor(name: ClassName) = classes.getOrPut(name) { TypeSpec.interfaceBuilder(name) }
    fun annotationFor(name: ClassName) = classes.getOrPut(name) { TypeSpec.annotationBuilder(name) }

    // 6.2 Generate classes, needed to back nodes
    fun wrappedName(node: LogicalNode, kind: DependencyKind): TypeName {
        return when (kind) {
            DependencyKind.Direct -> node.typeName
            DependencyKind.Lazy -> ClassNames.Lazy.parameterizedBy(node.typeName)
            DependencyKind.Provider -> ClassNames.Provider.parameterizedBy(node.typeName)
        }
    }

    fun FunSpec.Builder.generateParamsFrom(binding: Binding): FunSpec.Builder {
        var index = 0
        for ((depNode, kind) in binding.dependencies) {
            addParameter("p${index++}", wrappedName(depNode, kind))
        }
        return this
    }

    val aliasInfo: Map<LogicalNode, Set<LogicalNode>> = buildMap<LogicalNode, MutableSet<LogicalNode>> {
        scopes.walk { scope ->
            for (node in scope.value.nodes) {
                node.bindings.filterIsInstance<Binding.Alias>().forEach { alias ->
                    getOrPut(alias.source, ::mutableSetOf) += alias.target
                }
            }
        }
    }

    // Generate classes for nodes.
    scopes.walk { scope ->
        for (node in scope.value.nodes) {
            val isClass = node.bindings.any { it is Binding.Inject }
            val builder = if (isClass) {
                classFor(node.typeName as ClassName)
            } else {
                interfaceFor(node.typeName as ClassName)
            }

            for (alias in aliasInfo.getOrElse(node, ::emptySet)) {
                builder.addSuperinterface(alias.typeName)
            }

            val inject = node.bindings.singleOrNull { it is Binding.Inject }
            if (inject != null) {
                builder.primaryConstructor(FunSpec.constructorBuilder()
                    .generateParamsFrom(inject)
                    .addAnnotation(ClassNames.Inject)
                    .build())
                    .apply {
                        if (params.bindingScopes.roll(rng) == ProvisionScopes.Scoped) {
                            addAnnotation(node.scope.value.className)
                        }
                    }
            }
        }

        for (dependency in scope.value.componentDependencies) {
            interfaceFor(dependency.typeName)
                .apply {
                    for ((index, ep) in dependency.entryPoints.withIndex()) {
                        addFunction(
                            FunSpec.builder("ep_${index}")
                                .addModifiers(KModifier.ABSTRACT)
                                .returns(ep.target.typeName)
                                .build()
                        )
                    }
                }
        }
    }

    // 6.3 Generate modules' classes.
    for (module in allModules) {
        interfaceFor(module.value.className)
            .addAnnotation(AnnotationSpec.builder(ClassNames.Module)
                .addMember(CodeBlock.builder().apply {
                    add("includes = [")
                    for (child in module.children) {
                        add("%T::class,", child.value.className)
                    }
                    add("]")
                }.build())
                .addMember(CodeBlock.builder().apply {
                    add("subcomponents = [")
                    for (child in module.value.subcomponents) {
                        add("%T::class,", child.className)
                    }
                    add("]")
                }.build())
                .build())
            .apply {
                for (binding in module.value.bindings.filterIsInstance<Binding.Alias>()) {
                    addFunction(FunSpec.builder("alias_${id++}")
                        .addModifiers(KModifier.ABSTRACT)
                        .addAnnotation(ClassNames.Binds)
                        .returns(binding.target.typeName)
                        .addParameter("i", binding.source.typeName)
                        .build())
                }
            }
            .addType(TypeSpec.companionObjectBuilder()
                .apply {
                    for (binding in module.value.bindings.filterIsInstance<Binding.Provision>()) {
                        addFunction(FunSpec.builder("provides_${id++}")
                            .addAnnotation(ClassNames.Provides)
                            .apply {
                                if (params.bindingScopes.roll(rng) == ProvisionScopes.Scoped) {
                                    addAnnotation(binding.target.scope.value.className)
                                }
                            }
                            .returns(binding.target.typeName)
                            .generateParamsFrom(binding)
                            .addCode("return myMock<%T>()", binding.target.typeName)
                            .build()
                        )
                    }
                }
                .build())
    }

    // 6.5 Generate components' classes
    components.walk { component ->
        interfaceFor(component.value.className)
            .addAnnotation(AnnotationSpec
                .builder(ClassNames.Component)
                .apply {
                    if (component.parent != null) {
                        addMember("isRoot = false")
                    }
                }.addMember(CodeBlock.builder().apply {
                    add("modules = [")
                    for (moduleTree in component.value.modules.trees) {
                        add("%T::class,", moduleTree.value.className)
                    }
                    add("]")
                }.build()).addMember(CodeBlock.builder().apply {
                    add("dependencies = [")
                    component.value.scope.value.componentDependencies.forEach {
                        add("%T::class,", it.typeName)
                    }
                    add("]")
                }.build())
                .build()
            )
            .addAnnotation(component.value.scope.value.className)
            .apply {
                component.value.entryPoints.entries.forEachIndexed { index, (node, kind) ->
                    addProperty("get_$index", wrappedName(node, kind))
                }
                component.children.forEachIndexed { index, child ->
                    addProperty("childCreator_$index", child.value.creatorName)
                }
                component.value.memberInjectors.forEachIndexed { index, injector ->
                    addFunction(FunSpec.builder("inject_$index")
                        .addModifiers(KModifier.ABSTRACT)
                        .addParameter("i", injector.className)
                        .build())
                }
            }
            .apply {
                val creatorName = component.value.creatorName
                addType(TypeSpec.interfaceBuilder(creatorName)
                    .addAnnotation(ClassNames.ComponentBuilder)
                    .apply {
                        component.value.localBindings.values.filterIsInstance<Binding.Instance>()
                            .forEachIndexed { index, binding ->
                                addFunction(FunSpec.builder("set_$index")
                                    .addModifiers(KModifier.ABSTRACT)
                                    .returns(creatorName)
                                    .addParameter("i", binding.target.typeName)
                                    .addAnnotation(ClassNames.BindsInstance)
                                    .build())
                            }
                        component.value.scope.value.componentDependencies.forEachIndexed { index, dependency ->
                            addFunction(FunSpec.builder("setDep_$index")
                                .addModifiers(KModifier.ABSTRACT)
                                .returns(creatorName)
                                .addParameter("i", dependency.typeName)
                                .build())
                        }
                    }
                    .addFunction(FunSpec.builder("create")
                        .returns(component.value.className)
                        .addModifiers(KModifier.ABSTRACT)
                        .build())
                    .build())
            }
    }

    // 6.6 Generate classes for scopes
    run {
        val retentionSpec = AnnotationSpec.builder(ClassNames.Retention)
            .addMember("%T.RUNTIME", ClassNames.AnnotationRetention)
            .build()
        scopes.walk { scope ->
            annotationFor(scope.value.className)
                .addAnnotation(ClassNames.Scope)
                .addAnnotation(retentionSpec)
        }
    }

    // 6.7 Generate classes for Member Injectees.
    val allInjectors = buildSet { components.walk { addAll(it.value.memberInjectors) } }
    for (memberInjector in allInjectors) {
        interfaceFor(memberInjector.className)
            .apply {
                memberInjector.members.forEachIndexed { index, node ->
                    addProperty(PropertySpec.builder("member_$index", node.typeName)
                        .mutable()
                        .addModifiers(KModifier.ABSTRACT)
                        .addAnnotation(AnnotationSpec.builder(ClassNames.Inject)
                            .useSiteTarget(AnnotationSpec.UseSiteTarget.SET)
                            .build())
                        .build())
                }
            }
    }


    // 7. Flush the code to files.
    sourceDir.mkdirs()
    for ((name, builder) in classes) {
        FileSpec.builder(name.packageName, name.simpleName)
            .addAnnotation(AnnotationSpec.builder(ClassNames.Suppress)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                .addMember("%S", "UNUSED_PARAMETER")
                .build())  // Unless we do this, log is going to be flooded with warnings
            .addFileComment("THIS CODE IS GENERATED")
            .addType(builder.build())
            .build()
            .writeTo(sourceDir)
    }
    FileSpec.builder("test", "TestCase")
        .addFunction(FunSpec.builder("myMock")
            .addModifiers(KModifier.INLINE)
            .apply {
                val t = TypeVariableName("T", ClassNames.Any).copy(reified = true)
                addTypeVariable(t)
                returns(t)
                addCode("return %M<%T>(stubOnly = true, defaultAnswer = %T.RETURNS_DEEP_STUBS)",
                    ClassNames.mock, t, ClassNames.Answers)
            }
            .build())
        .addFunction(FunSpec.builder(testMethodName)
            .addCode(CodeBlock.builder()
                .apply {
                    // TODO: split this method into many to overcome method length limit.
                    var globalIndex = 0
                    fun instantiateComponent(component: Tree<Component>, creatorVar: String? = null) {
                        val componentIndex = globalIndex++
                        add("val component$componentIndex = ")
                        if (creatorVar != null) {
                            add("%L", creatorVar)
                        } else {
                            add("%T.builder(%T::class.java)", ClassNames.Dagger, component.value.creatorName)
                        }
                        component.value.localBindings.values
                            .filterIsInstance<Binding.Instance>().forEachIndexed { setterIndex, _ ->
                                add(".set_$setterIndex(myMock())")
                            }
                        component.value.scope.value.componentDependencies.forEachIndexed { dependencyIndex, _ ->
                            add(".setDep_$dependencyIndex(myMock())")
                        }
                        addStatement(".create()")
                        component.value.entryPoints.entries.forEachIndexed { entryPointIndex, (_, kind) ->
                            val unLazy = when (kind) {
                                DependencyKind.Direct -> ""
                                else -> ".get()"
                            }
                            addStatement("component$componentIndex.get_$entryPointIndex$unLazy")
                        }
                        component.value.memberInjectors.forEachIndexed { injIndex, _ ->
                            addStatement("component$componentIndex.inject_$injIndex(myMock())")
                        }
                        component.children.forEachIndexed { chIndex, child ->
                            val creatorName = "creator${componentIndex}_$chIndex"
                            addStatement("val $creatorName = component$componentIndex.childCreator_$chIndex")
                            instantiateComponent(child, creatorVar = creatorName)
                        }
                    }
                    components.trees.forEach { root ->
                        instantiateComponent(root)
                    }
                }
                .build())
            .build())
        .build()
        .writeTo(sourceDir)
}
