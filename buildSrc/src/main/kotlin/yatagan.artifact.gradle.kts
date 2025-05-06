import com.yandex.yatagan.gradle.isValidSemVerString
import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.kotlin.dsl.registering

plugins {
    id("yatagan.base-module")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

val yataganVersion: String by extra

check(isValidSemVerString(yataganVersion)) {
    "`$yataganVersion` is not a valid version"
}

// For release publications
val mavenUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_URL")

// For snapshot version publications
val mavenSnapshotUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_SNAPSHOT_URL")

// maven username - must be valid both for snapshot and release repos.
// WARNING: For nexus (sonatype) publications, use NEXUS_USERNAME variable.
val mavenUsername: Provider<String> = providers.environmentVariable("MAVEN_USERNAME")

// maven password - must be valid both for snapshot and release repos.
// WARNING: For nexus (sonatype) publications, use NEXUS_PASSWORD variable.
val mavenPassword: Provider<String> = providers.environmentVariable("MAVEN_PASSWORD")

val signingKeyId: Provider<String> = providers.environmentVariable("MAVEN_SIGNING_KEY_ID")
val signingPassword: Provider<String> = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")
val signingSecretKey: Provider<String> = providers.environmentVariable("MAVEN_SIGNING_SECRET_KEYRING_FILE")

val isPublishToMavenEnabled = (mavenUrl.isPresent || mavenSnapshotUrl.isPresent)
        && mavenUsername.isPresent && mavenPassword.isPresent

java {
    withSourcesJar()
}

val artifactName = path.trim(':').replace(':', '-')

tasks.withType<DokkaTask>().configureEach {
    moduleName.set(artifactName)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc.map { it.outputDirectory })
    dependsOn(tasks.dokkaJavadoc)
}

artifacts {
    add(configurations.archives.name, javadocJar)
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["java"])
            artifact(javadocJar)

            this.version = yataganVersion
            this.groupId = "com.yandex.yatagan"
            this.artifactId = artifactName

            pom {
                name.set("Yatagan")
                description.set("Yatagan is a Dependency Injection framework, " +
                        "specializing on runtime performance and build speed. " +
                        "Supports code generation (apt/kapt/ksp) or reflection.")
                url.set("http://github.com/yandex/yatagan/")

                licenses {
                    license {
                        name.set("Apache License, version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }

                scm {
                    connection.set("scm:git://github.com/yandex/yatagan.git")
                    developerConnection.set("scm:git://github.com/yandex/yatagan.git")
                    url.set("https://github.com/yandex/yatagan.git")
                }

                developers {
                    developer {
                        name.set("Yandex LLC")
                        url.set("https://yandex.com")
                    }
                }
            }
        }
    }

    if (isPublishToMavenEnabled) {
        repositories {
            fun MavenArtifactRepository.setupCredentials() {
                credentials {
                    username = mavenUsername.get()
                    password = mavenPassword.get()
                }
            }

            val isSnapshotVersion = yataganVersion.endsWith("SNAPSHOT")
            when {
                !isSnapshotVersion && mavenUrl.isPresent -> maven {
                    url = uri(mavenUrl.get())
                    setupCredentials()
                }
                isSnapshotVersion && mavenSnapshotUrl.isPresent -> maven {
                    url = uri(mavenSnapshotUrl.get())
                    setupCredentials()
                }
            }
        }
    }
}

if (signingKeyId.isPresent && signingPassword.isPresent && signingSecretKey.isPresent) {
    signing {
        sign(publishing.publications)
        sign(configurations.archives.get())

        useInMemoryPgpKeys(
            signingKeyId.get(),
            signingSecretKey.get(),
            signingPassword.get(),
        )
    }
}

rootProject.tasks {
    // Every actual publish task must run after the root publish task, if any.
    findByName("publish")?.let { rootPublish ->
        tasks.publish {
            mustRunAfter(rootPublish)
        }
    }
}