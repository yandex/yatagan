import com.yandex.daggerlite.gradle.isValidSemVerString

plugins {
    id("daggerlite.base-module")
    `maven-publish`
}

// dagger-lite version
val versionString: String = providers.fileContents(rootProject.layout.projectDirectory.file("daggerlite.version"))
    .asText.forUseAtConfigurationTime().get().trim()

check(isValidSemVerString(versionString)) {
    "`$versionString` is not a valid version"
}

// For release publications
val mavenUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_URL")
    .forUseAtConfigurationTime()

// For snapshot version publications
val mavenSnapshotUrl: Provider<String> = providers.environmentVariable("MAVEN_REPOSITORY_SNAPSHOT_URL")
    .forUseAtConfigurationTime()

// maven username - must be valid both for snapshot and release repos.
val mavenUsername: Provider<String> = providers.environmentVariable("MAVEN_USERNAME")
    .forUseAtConfigurationTime()

// maven password - must be valid both for snapshot and release repos.
val mavenPassword: Provider<String> = providers.environmentVariable("MAVEN_PASSWORD")
    .forUseAtConfigurationTime()

version = versionString
group = "com.yandex.daggerlite"

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>(name) {
            from(components["java"])
        }

        repositories {
            fun MavenArtifactRepository.setupCredentials() {
                credentials {
                    username = mavenUsername.get()
                    password = mavenPassword.get()
                }
            }

            val isSnapshotVersion = versionString.endsWith("SNAPSHOT")
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
