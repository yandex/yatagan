import io.github.gradlenexus.publishplugin.NexusPublishExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("yatagan.base-module")
    id("org.jetbrains.kotlinx.kover")
}

val yataganVersion: String by extra
val enableCoverage: Boolean by extra

val isUnderTeamcity = providers.environmentVariable("TEAMCITY_VERSION").isPresent

val nexusUsername: Provider<String> = providers.environmentVariable("NEXUS_USERNAME")
val nexusPassword: Provider<String> = providers.environmentVariable("NEXUS_PASSWORD")

if (nexusUsername.isPresent && nexusPassword.isPresent) {
    apply(plugin = "io.github.gradle-nexus.publish-plugin")
    extensions.configure<NexusPublishExtension> {
        this.repositories {
            sonatype {
                nexusUrl.set(uri("https://oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://oss.sonatype.org/content/repositories/snapshots/"))

                username.set(nexusUsername)
                password.set(nexusPassword)

                packageGroup.set("com.yandex.yatagan")
            }
        }
    }
}

tasks {
    if (isUnderTeamcity) {
        register("publish") {
            doLast {
                println("##teamcity[buildStatus text='${yataganVersion}: {build.status.text}']")
            }
        }
    }
}

koverReport {
    filters {
        includes {
            classes("com.yandex.yatagan.**")
        }
        excludes {
            classes(
                // Do not cover internal stuff
                "**.internal.**",
                // Mostly inline utility code
                "**.ExtensionsKt",
                // Testing code
                "com.yandex.yatagan.testing.**",
                // Code generation utility code
                "com.yandex.yatagan.codegen.poetry.**",
                // Just in case some build code gets instrumented
                "com.yandex.yatagan.gradle.**",
            )
        }
    }
}

val projectsToCover = setOf(
    ":api:public",
    ":api:dynamic",
    ":validation:impl",
    ":validation:format",
    ":lang:common",
    ":lang:compiled",
    ":lang:jap",
    ":lang:rt",
    ":lang:ksp",
    ":core:model:impl",
    ":core:graph:impl",
    ":processor:common",
    ":processor:jap",
    ":processor:ksp",
    ":rt:engine",
    ":rt:support",
    ":codegen:impl",
    ":testing:tests",
)

val projectsNotToCover = setOf(
    // Do not cover utility
    ":base:api",
    ":base:impl",

    // Do not cover "api" artefacts, as they contain no logic but sometimes can mess with the numbers
    ":api:compiled",  // DEPRECATED
    ":validation:api",
    ":validation:spi",
    ":lang:api",
    ":core:model:api",
    ":core:graph:api",

    // Do not cover codegen utility
    ":codegen:poetry",

    // Do not cover testing harness
    ":testing:procedural",
    ":testing:source-set",
)

projectsNotToCover.intersect(projectsToCover).let { ambiguous ->
    check(ambiguous.isEmpty()) {
        "Code coverage configuration for $ambiguous projects is ambiguous. " +
                "Ensure each project path is present in only one list"
    }
}

fun KoverProjectExtension.configureKover() {
    // IntelliJ engine yields weird results with uncovered empty lines.
    // Results from Jacoco are pretty accurate.
    useJacoco()
}

if (enableCoverage) {
    kover.configureKover()

    subprojects {
        when (path) {
            in projectsToCover -> {
                apply(plugin = "org.jetbrains.kotlinx.kover")
                extensions.getByType<KoverProjectExtension>().configureKover()
            }

            in projectsNotToCover -> {
                // Okay, explicitly skip it
            }

            else -> afterEvaluate {
                // If the project contains code (not a "namespace" project)
                if (plugins.hasPlugin("java")) {
                    throw GradleException("Please, decide whether to enable code coverage for '$path' project " +
                            "and put it into the appropriate list")
                }
            }
        }
    }

    dependencies {
        projectsToCover.forEach {
            kover(project(it))
        }
    }

    koverReport {
        defaults {
            xml {
                onCheck = true
            }
        }
    }
}