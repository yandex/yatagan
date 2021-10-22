package com.yandex.daggerlite.core

/**
 * An entity that is backed by a class definition.
 */
abstract class ClassBackedModel {
    /**
     * Class id (name or some other entity, that can uniquely represent a class).
     * [equals]/[hashCode] work is based on this id.
     */
    abstract val id: Id

    interface Id {
        override fun equals(other: Any?): Boolean
        override fun hashCode(): Int
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is ClassBackedModel && id == other.id
    }

    override fun hashCode() = id.hashCode()
}