package com.yandex.daggerlite.gradle

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

const val RepositoryBrowseUrl = "https://bitbucket.browser.yandex-team.ru/projects/ML/repos/dagger-lite/browse"

@Suppress("UNCHECKED_CAST")
fun AttributeContainer.copyFrom(another: AttributeContainer) {
    for (keyAttribute: Attribute<*> in another.keySet()) {
        keyAttribute as Attribute<Any?>
        val value = another.getAttribute(keyAttribute)!!
        attribute(keyAttribute, value)
    }
}

abstract class ComponentFactoryProvider @Inject constructor(
    val softwareComponentFactory: SoftwareComponentFactory,
)

fun Project.publishedArtifactName(): String = project.extensions.findByType<PublishingExtension>()?.let {
    val mainArtifactPublication = it.publications.getByName("main") as MavenPublication
    mainArtifactPublication.artifactId
} ?: project.name
