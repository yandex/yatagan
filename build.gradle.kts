import com.yandex.yatagan.gradle.PatchModuleDocTask
import com.yandex.yatagan.gradle.RepositoryBrowseUrl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration

plugins {
    id("yatagan.base-module")
    id("org.jetbrains.dokka")
}

val yataganVersion: String by extra

val patchRootDoc by tasks.registering(PatchModuleDocTask::class) {
    inputFile.set(file("docs/yatagan.md"))
    macros.putAll(mapOf(
        "version" to yataganVersion,
        "kotlin_version" to libs.versions.kotlin.get(),
        "repo_link" to RepositoryBrowseUrl,
    ))
}

tasks.dokkaHtmlMultiModule {
    dependsOn(patchRootDoc)

    outputDirectory.set(layout.buildDirectory.dir("docs").map { it.asFile })
    includes.from(patchRootDoc.map { it.outputFile })

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "<div>Copyright 2022 Yandex LLC. All rights reserved.<p/>" +
                """Slack: <a href="https://yndx-browser.slack.com/archives/C02J7BZLY0L">#dagger-lite-dev</a></div>"""
        customStyleSheets = listOf(file("docs/logo-styles.css"))
    }
}

val isUnderTeamcity = providers.environmentVariable("TEAMCITY_VERSION").isPresent

tasks {
    check {
        // Check docs generation
        dependsOn(dokkaHtmlMultiModule)
    }

    if (isUnderTeamcity) {
        register("publish") {
            doLast {
                println("##teamcity[buildStatus text='${yataganVersion}: {build.status.text}']")
            }
        }
    }
}