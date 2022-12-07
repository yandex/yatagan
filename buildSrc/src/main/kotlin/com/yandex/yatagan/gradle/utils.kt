package com.yandex.yatagan.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
import javax.inject.Inject

// See https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
// See https://regex101.com/r/vkijKf/1/
private val semVerRegex = ("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
        "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$").toRegex()

fun isValidSemVerString(string: String): Boolean {
    return semVerRegex.matches(string)
}
