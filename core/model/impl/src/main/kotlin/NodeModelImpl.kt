package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.model.ConditionalHoldingModel
import com.yandex.daggerlite.core.model.DependencyKind
import com.yandex.daggerlite.core.model.HasNodeModel
import com.yandex.daggerlite.core.model.InjectConstructorModel
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.Annotated
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.Constructor
import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.getListType
import com.yandex.daggerlite.lang.getProviderType
import com.yandex.daggerlite.lang.getSetType
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class NodeModelImpl private constructor(
    override val type: Type,
    override val qualifier: Annotation?,
) : NodeModel, NodeDependency {

    init {
        assert(!type.isVoid) {
            "Not reached: void can't be represented as NodeModel"
        }
    }

    private inner class InjectConstructorImpl(
        override val constructor: Constructor,
    ) : InjectConstructorModel, ConditionalHoldingModel {
        init {
            assert(constructor.getAnnotation(BuiltinAnnotation.Inject) != null)
        }

        private val conditionalModel by lazy {
            ConditionalHoldingModelImpl(constructor.constructee.getAnnotations(BuiltinAnnotation.Conditional))
        }

        override val conditionals: List<ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel>
            get() = conditionalModel.conditionals

        override val inputs: List<NodeDependency> by lazy {
            constructor.parameters.map { param ->
                NodeDependency(type = param.type, forQualifier = param)
            }.toList()
        }

        override val type: Type = this@NodeModelImpl.type

        override fun asNode(): NodeModel = this@NodeModelImpl

        override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
            return visitor.visitInjectConstructor(this)
        }

        override val scopes: Set<Annotation> by lazy {
            constructor.constructee.annotations.filter { it.isScope() }.toSet()
        }

        override fun validate(validator: Validator) {
            validator.child(conditionalModel)
            if (!constructor.isEffectivelyPublic || !constructor.constructee.isEffectivelyPublic) {
                validator.reportError(Strings.Errors.invalidAccessInjectConstructor())
            }
            for (input in inputs) {
                validator.child(input.node)
            }
        }

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "@inject",
            representation = {
                append("$type(")
                when(childContext) {
                    is NodeModel -> {
                        val index = inputs.indexOfFirst { it.node == childContext }
                        append(".., ")
                        appendChildContextReference(reference = constructor.parameters.drop(index).first())
                        append(", ..)")
                    }
                    else -> {
                        append("...)")
                    }
                }
            },
        )
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Inherit
        qualifier?.let {
            appendRichString {
                color = TextColor.Green
                append(qualifier)
            }
            append(" ")
        }
        append(type)
    }

    override fun multiBoundListNodes(): Array<NodeModel> {
        return arrayOf(
            Factory(type = LangModelFactory.getListType(type, isCovariant = false), qualifier = qualifier),
            Factory(type = LangModelFactory.getListType(type, isCovariant = true), qualifier = qualifier),
        )
    }

    override fun multiBoundSetNodes(): Array<NodeModel> {
        return arrayOf(
            Factory(type = LangModelFactory.getSetType(type, isCovariant = false), qualifier = qualifier),
            Factory(type = LangModelFactory.getSetType(type, isCovariant = true), qualifier = qualifier),
        )
    }

    override fun multiBoundMapNodes(key: Type, asProviders: Boolean): Array<NodeModel> {
        val keyType = key.asBoxed()  // Need to use box as key may be a primitive type
        val valueType = if (asProviders) LangModelFactory.getProviderType(type) else type
        return arrayOf(
            Factory(type = LangModelFactory.getMapType(keyType, valueType, isCovariant = false), qualifier = qualifier),
            Factory(type = LangModelFactory.getMapType(keyType, valueType, isCovariant = true), qualifier = qualifier),
        )
    }

    override val hintIsFrameworkType: Boolean
        get() = isFrameworkType(type)

    override fun validate(validator: Validator) {
        if (isFrameworkType(type)) {
            validator.reportError(Strings.Errors.manualFrameworkType())
        }
    }

    override fun getSpecificModel(): HasNodeModel? {
        val declaration = type.declaration
        val inject = if (qualifier == null) {
            declaration.constructors.find { it.getAnnotation(BuiltinAnnotation.Inject) != null }
        } else null
        return when {
            inject != null -> InjectConstructorImpl(inject)
            AssistedInjectFactoryModelImpl.canRepresent(declaration) -> AssistedInjectFactoryModelImpl(declaration)
            ComponentFactoryModelImpl.canRepresent(declaration) -> ComponentFactoryModelImpl(declaration)
            ComponentModelImpl.canRepresent(declaration) -> ComponentModelImpl(declaration)
            else -> null
        }
    }

    override fun dropQualifier(): NodeModel {
        if (qualifier == null) return this
        return Factory(type = type, qualifier = null)
    }

    override fun compareTo(other: NodeModel): Int {
        return type.compareTo(other.type)
    }

    override val node: NodeModel
        get() = this

    override val kind: DependencyKind
        get() = DependencyKind.Direct

    override fun copyDependency(node: NodeModel, kind: DependencyKind) = when(kind) {
        DependencyKind.Direct -> node
        else -> NodeDependencyImpl(node = node, kind = kind)
    }

    companion object Factory : ObjectCache<Any, NodeModelImpl>() {
        class VoidNode : NodeModel {
            override val type get() = LangModelFactory.errorType
            override val qualifier: Nothing? get() = null
            override fun getSpecificModel(): Nothing? = null
            override fun dropQualifier(): NodeModel = this
            override fun multiBoundListNodes(): Array<NodeModel> = emptyArray()
            override fun multiBoundSetNodes(): Array<NodeModel> = emptyArray()
            override fun multiBoundMapNodes(key: Type, asProviders: Boolean): Array<NodeModel> = emptyArray()
            override fun validate(validator: Validator) {
                validator.reportError(Strings.Errors.voidBinding())
            }
            override val hintIsFrameworkType: Boolean get() = false
            override fun compareTo(other: NodeModel): Int = hashCode() - other.hashCode()
            override val node: NodeModel get() = this
            override val kind: DependencyKind get() = DependencyKind.Direct
            override fun copyDependency(node: NodeModel, kind: DependencyKind) = when(kind) {
                DependencyKind.Direct -> node
                else -> NodeDependencyImpl(node = node, kind = kind)
            }
            override fun toString(childContext: MayBeInvalid?) = buildRichString {
                color = TextColor.Red
                append("<invalid node: void>")
            }
        }

        operator fun invoke(
            type: Type,
            forQualifier: Annotated?,
        ) = this(type, forQualifier?.annotations?.find(Annotation::isQualifier))

        operator fun invoke(
            type: Type,
            qualifier: Annotation? = null,
        ): NodeModelImpl {
            val boxed = type.asBoxed()
            val key: Any = if (qualifier != null) boxed to qualifier else boxed
            return createCached(key) {
                NodeModelImpl(
                    type = boxed,
                    qualifier = qualifier,
                )
            }
        }
    }
}