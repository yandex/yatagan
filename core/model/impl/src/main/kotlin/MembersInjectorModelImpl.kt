package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.MembersInjectorModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.reportMandatoryWarning

internal class MembersInjectorModelImpl private constructor(
    override val injector: Method,
) : MembersInjectorModel {
    init {
        assert(canRepresent(injector))
    }

    private val injectee = injector.parameters.single().type

    override val membersToInject: Map<Member, NodeDependency> by lazy {
        buildMap {
            injectee.declaration.fields.filter {
                it.getAnnotation(BuiltinAnnotation.Inject) != null
            }.forEach { fieldInjectee ->
                put(fieldInjectee, NodeDependency(
                    type = fieldInjectee.type,
                    forQualifier = fieldInjectee,
                ))
            }
            injectee.declaration.methods.filter {
                it.getAnnotation(BuiltinAnnotation.Inject) != null
            }.forEach { functionInjectee ->
                put(functionInjectee, NodeDependency(
                    type = functionInjectee.parameters.single().type,
                    forQualifier = functionInjectee,
                ))
            }
        }
    }

    override fun validate(validator: Validator) {
        membersToInject.forEach { (member, dependency) ->
            validator.child(dependency.node)
            if (!member.isEffectivelyPublic) {
                validator.reportError(Strings.Errors.invalidAccessForMemberToInject(member = member))
            }
        }

        for ((name, members) in membersToInject.keys.filter { it.accept(IsField) }.groupBy { it.name }) {
            if (members.size > 1) {
                validator.reportMandatoryWarning(Strings.Warnings.fieldInjectShadow(name = name))
            }
        }

        if (!injector.returnType.isVoid) {
            validator.reportError(Strings.Errors.invalidInjectorReturn())
        }
        if (!injectee.declaration.isEffectivelyPublic) {
            validator.reportError(Strings.Errors.invalidAccessForMemberInject())
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "injector-function",
        representation = {
            append("${injector.name}(${injector.parameters.single()})")
            if (childContext is NodeModel) {
                val (member, _) = membersToInject.entries.find { (_, dependency) ->
                    dependency.node == childContext
                }!!
                append(" { .., ")
                appendChildContextReference(reference = member)
                append(", .. }")
            }
        },
    )

    companion object Factory : ObjectCache<Method, MembersInjectorModelImpl>() {
        operator fun invoke(injector: Method) = createCached(injector, ::MembersInjectorModelImpl)

        fun canRepresent(impl: Method): Boolean {
            return impl.isAbstract && impl.parameters.count() == 1
        }

        private object IsField : Member.Visitor<Boolean> {
            override fun visitMethod(model: Method) = false
            override fun visitField(model: Field) = true
        }
    }
}