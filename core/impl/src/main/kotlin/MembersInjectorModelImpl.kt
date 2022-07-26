package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import com.yandex.daggerlite.validation.format.reportMandatoryWarning
import javax.inject.Inject

internal class MembersInjectorModelImpl private constructor(
    override val injector: FunctionLangModel,
) : MembersInjectorModel {
    init {
        assert(canRepresent(injector))
    }

    private val injectee = injector.parameters.single().type

    override val membersToInject: Map<MemberLangModel, NodeDependency> by lazy {
        buildMap {
            injectee.declaration.fields.filter {
                it.isAnnotatedWith<Inject>()
            }.forEach { fieldInjectee ->
                put(fieldInjectee, NodeDependency(
                    type = fieldInjectee.type,
                    forQualifier = fieldInjectee,
                ))
            }
            injectee.declaration.functions.filter {
                it.isAnnotatedWith<Inject>()
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

    override fun toString() = "[injector-fun] ${injector.name}: $injectee"

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

    companion object Factory : ObjectCache<FunctionLangModel, MembersInjectorModelImpl>() {
        operator fun invoke(injector: FunctionLangModel) = createCached(injector, ::MembersInjectorModelImpl)

        fun canRepresent(impl: FunctionLangModel): Boolean {
            return impl.isAbstract && impl.parameters.count() == 1
        }

        private object IsField : MemberLangModel.Visitor<Boolean> {
            override fun visitFunction(model: FunctionLangModel) = false
            override fun visitField(model: FieldLangModel) = true
        }
    }
}