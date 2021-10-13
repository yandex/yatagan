package com.yandex.dagger3.core

/**
 * An entity that is backed by a class definition.
 */
interface ClassBackedModel {
    /**
     * Class name
     */
    val name: ClassNameModel
}