package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Callable
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type

/**
 * A [com.yandex.yatagan.Binds] binding.
 * Sort of fictional binding, that must be resolved into some real [Binding].
 */
public interface AliasBinding : BaseBinding {
    /**
     * Actual implementation node to be used (aliased for) for compatible [target].
     */
    public val source: NodeModel
}

/**
 * A specific case of [com.yandex.yatagan.Binds] binding with multiple alternatives.
 */
public interface AlternativesBinding : Binding {
    public val alternatives: Sequence<NodeModel>
}

public interface AssistedInjectFactoryBinding : Binding {
    public val model: AssistedInjectFactoryModel
}

public interface ComponentDependencyBinding : Binding {
    public val dependency: ComponentDependencyModel
}

public interface ComponentDependencyEntryPointBinding : Binding {
    public val dependency: ComponentDependencyModel
    public val getter: Method
}

public interface ComponentInstanceBinding : Binding

/**
 * Binding that can not be satisfied - it's codegen or runtime evaluation is *unreached*.
 *
 * Empty [com.yandex.yatagan.Binds] binding is an example of such binding.
 */
public interface EmptyBinding : Binding

/**
 * A [com.yandex.yatagan.BindsInstance] binding.
 * Introduced into a graph as [com.yandex.yatagan.core.model.ComponentFactoryModel.InputModel].
 */
public interface InstanceBinding : Binding

public interface MapBinding : ExtensibleBinding<MapBinding> {
    public val mapKey: Type
    public val mapValue: Type

    /**
     * NOTE: Dependency resolve should be done exactly on the binding's [owner].
     */
    public val contents: Collection<Contribution>

    /**
     * A pair of [keyValue], [dependency] to be put into map.
     */
    public interface Contribution {
        /**
         * A value of a key annotation (read: map key)
         */
        public val keyValue: Annotation.Value

        /**
         * A dependency which resolves to a contribution for the key.
         */
        public val dependency: NodeDependency
    }
}

/**
 * A binding for a `List<T>` aggregating all contributions for `T`, marked with [com.yandex.yatagan.IntoList].
 * Such bindings exhibit "extends" behavior: bindings for the same list in child graphs inherit all the contributions
 *  from parent ones (in a cascading way) and are not considered conflicting.
 */
public interface MultiBinding : ExtensibleBinding<MultiBinding> {
    /**
     * All list contributions.
     */
    public val contributions: Map<NodeModel, ContributionType>

    /**
     * Target collection kind.
     */
    public val kind: CollectionTargetKind

    public enum class ContributionType {
        /**
         * Single element to be contributed to a collection.
         */
        Element,

        /**
         * Elements of a collection to be contributed.
         */
        Collection,
    }
}

/**
 * A [com.yandex.yatagan.Provides] binding.
 */
public interface ProvisionBinding : Binding {
    public val provision: Callable
    public val inputs: List<NodeDependency>
    public val requiresModuleInstance: Boolean
}

public interface SubComponentFactoryBinding : Binding {
    public val targetGraph: BindingGraph
}