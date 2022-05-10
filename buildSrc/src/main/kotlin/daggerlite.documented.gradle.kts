import com.yandex.daggerlite.gradle.PatchModuleDocTask
import com.yandex.daggerlite.gradle.RepositoryBrowseUrl
import org.intellij.lang.annotations.Language
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

val kdocsTestsDir = layout.buildDirectory.dir("kdocs-tests-dir")

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

val dokkaTask = tasks.named<DokkaTaskPartial>("dokkaHtmlPartial") {
    dependsOn(patchModuleDoc)

    dependencies {
        plugins(project(":testing-dokka"))
    }

    dokkaSourceSets {
        named("main") {
            noStdlibLink.set(true)
            noJdkLink.set(true)

            includes.from("module.md")

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

    @Language("JSON")
    val config = """
        {
          "codeBlockTestsOutputDirectory": "${kdocsTestsDir.get().asFile}"
        }
    """.trimIndent()
    pluginsMapConfiguration.set(mapOf(
        "com.yandex.daggerlite.testing.dokka.DLDokkaPlugin" to config
    ))

    outputs.dir(kdocsTestsDir)
}

val runCodeBlockTestsClasspath by configurations.creating

dependencies {
    runCodeBlockTestsClasspath(project(":testing"))
}

val testDocumentationCodeBlocks by tasks.registering(JavaExec::class) {
    description = "Runs code block tests to verify documentation sample code's integrity"
    group = "verification"

    dependsOn(dokkaTask)
    inputs.dir(kdocsTestsDir)

    classpath = runCodeBlockTestsClasspath
    mainClass.set("com.yandex.daggerlite.testing.Standalone")
    argumentProviders += CommandLineArgumentProvider {
        listOf("--backend", "ksp", "--test-cases-dir", kdocsTestsDir.get().asFile.toString())
    }
}

tasks.check {
    dependsOn(testDocumentationCodeBlocks)
}