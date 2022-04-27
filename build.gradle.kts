import com.yandex.daggerlite.gradle.PatchModuleDocTask
import com.yandex.daggerlite.gradle.RepositoryBrowseUrl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration

plugins {
    id("daggerlite.base-module")
    id("org.jetbrains.dokka")
}

val daggerLiteVersion: String by extra
val kotlinVersion: String by extra

val patchRootDoc by tasks.registering(PatchModuleDocTask::class) {
    inputFile.set(file("docs/dagger-lite.md"))
    macros.putAll(mapOf(
        "version" to daggerLiteVersion,
        "kotlin_version" to kotlinVersion,
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