import com.yandex.yatagan.gradle.isValidSemVerString

plugins {
    id("daggerlite.base-module")
    `maven-publish`
}

val daggerLiteVersion: String by extra

check(isValidSemVerString(daggerLiteVersion)) {
    "`$daggerLiteVersion` is not a valid version"
}

// For release publications
val mavenUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_URL")

// For snapshot version publications
val mavenSnapshotUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_SNAPSHOT_URL")

// maven username - must be valid both for snapshot and release repos.
val mavenUsername: Provider<String> = providers.environmentVariable("MAVEN_USERNAME")

// maven password - must be valid both for snapshot and release repos.
val mavenPassword: Provider<String> = providers.environmentVariable("MAVEN_PASSWORD")

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["java"])
            this.version = daggerLiteVersion
            this.groupId = "com.yandex.yatagan"
            this.artifactId = path.trim(':').replace(':', '-')
        }

        components.findByName("optimizedJava")?.let { optimizedJava ->
            create<MavenPublication>("optimized") {
                from(optimizedJava)
                artifactId = "${project.name}-optimized"
            }
        }

        repositories {
            fun MavenArtifactRepository.setupCredentials() {
                credentials {
                    username = mavenUsername.get()
                    password = mavenPassword.get()
                }
            }

            val isSnapshotVersion = daggerLiteVersion.endsWith("SNAPSHOT")
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

rootProject.tasks {
    // Every actual publish task must run after the root publish task, if any.
    findByName("publish")?.let { rootPublish ->
        tasks.publish {
            mustRunAfter(rootPublish)
        }
    }
}