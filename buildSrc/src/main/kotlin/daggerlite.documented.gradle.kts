import com.yandex.daggerlite.gradle.PatchModuleDocTask
import com.yandex.daggerlite.gradle.RepositoryBrowseUrl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.io.ByteArrayOutputStream
import java.net.URL

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

val samplesDir = file("samples/")

val patchModuleDoc by tasks.registering(PatchModuleDocTask::class) {
    inputFile.set(file("module.md"))
}

fun gitCommitHash(): String {
    return ByteArrayOutputStream().apply bytes@ {
        exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = this@bytes
        }.assertNormalExitValue()
    }.toString().trim()
}

kotlin {
    sourceSets {
        test {
            kotlin.srcDir(samplesDir)
        }
    }
}

tasks.withType<DokkaTaskPartial> {
    dependsOn(patchModuleDoc)
    dokkaSourceSets {
        named("main") {
            noStdlibLink.set(true)
            noJdkLink.set(true)

            includes.from(patchModuleDoc.map { it.outputFile })

            // Include all samples from `samples` project directory, if present
            if (samplesDir.isDirectory) {
                samples.from(samplesDir)
            }

            // Source links are set up for Atlassian Bitbucket.
            sourceLink {
                val sourceSetDir = file("src/main/kotlin")
                localDirectory.set(sourceSetDir)
                val relativeSourceSetDir = sourceSetDir.absoluteFile.relativeTo(rootProject.rootDir.absoluteFile)
                remoteUrl.set(URL("$RepositoryBrowseUrl/$relativeSourceSetDir"))
                remoteLineSuffix.set("?at=${gitCommitHash()}#")
            }
        }
    }

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = "<div>Copyright 2022 Yandex LLC. All rights reserved.<p/>" +
                """Slack: <a href="https://yndx-browser.slack.com/archives/C02J7BZLY0L">#dagger-lite-dev</a></div>"""
    }
}
