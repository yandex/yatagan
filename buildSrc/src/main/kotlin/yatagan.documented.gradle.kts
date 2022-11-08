import com.yandex.yatagan.gradle.PatchModuleDocTask
import com.yandex.yatagan.gradle.publishedArtifactName
import com.yandex.yatagan.gradle.RepositoryBrowseUrl
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
        plugins(project(":testing:doc-testing"))
    }

    doFirst {
        // Clean of any stale files.
        delete(kdocsTestsDir)
    }

    doLast {
        // Ensure created in case no output was done.
        mkdir(kdocsTestsDir)
    }

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

    @Language("JSON")
    val config = """
        {
          "codeBlockTestsOutputDirectory": "${kdocsTestsDir.get().asFile}"
        }
    """.trimIndent()
    pluginsMapConfiguration.set(mapOf(
        "com.yandex.yatagan.testing.doc_testing.DLDokkaPlugin" to config
    ))

    moduleName.set(project.publishedArtifactName())

    outputs.dir(kdocsTestsDir)
}

val runCodeBlockTestsClasspath by configurations.creating

dependencies {
    runCodeBlockTestsClasspath(project(":testing:tests"))
}

val testDocumentationCodeBlocks by tasks.registering(JavaExec::class) {
    description = "Runs code block tests to verify documentation sample code's integrity"
    group = "verification"

    dependsOn(dokkaTask)
    inputs.dir(kdocsTestsDir)

    classpath = runCodeBlockTestsClasspath
    mainClass.set("com.yandex.yatagan.testing.tests.Standalone")
    argumentProviders += CommandLineArgumentProvider {
        listOf("--backend", "ksp", "--test-cases-dir", kdocsTestsDir.get().asFile.toString())
    }
}

tasks.check {
    dependsOn(testDocumentationCodeBlocks)
}