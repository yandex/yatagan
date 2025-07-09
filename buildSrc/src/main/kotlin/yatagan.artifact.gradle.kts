import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost
import com.yandex.yatagan.gradle.isValidSemVerString

plugins {
    id("yatagan.base-module")
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    id("com.vanniktech.maven.publish")
}

val yataganVersion: String by extra

check(isValidSemVerString(yataganVersion)) {
    "`$yataganVersion` is not a valid version"
}

val artifactName = path.trim(':').replace(':', '-')

dokka {
    moduleName.set(artifactName)
}

mavenPublishing {
    publishToMavenCentral()
    configure(KotlinJvm(
        javadocJar = JavadocJar.Dokka(tasks.dokkaGeneratePublicationJavadoc.name),
        sourcesJar = true,
    ))

    coordinates(
        groupId = "com.yandex.yatagan",
        artifactId = artifactName,
        version = yataganVersion,
    )

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

rootProject.tasks {
    // Every actual publish task must run after the root publish task, if any.
    findByName("publish")?.let { rootPublish ->
        tasks.publish {
            mustRunAfter(rootPublish)
        }
    }
}
