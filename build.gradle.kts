import io.github.gradlenexus.publishplugin.NexusPublishExtension

plugins {
    id("yatagan.base-module")
}

val yataganVersion: String by extra

val isUnderTeamcity = providers.environmentVariable("TEAMCITY_VERSION").isPresent

val nexusUsername: Provider<String> = providers.environmentVariable("NEXUS_USERNAME")
val nexusPassword: Provider<String> = providers.environmentVariable("NEXUS_PASSWORD")

if (nexusUsername.isPresent && nexusPassword.isPresent) {
    apply(plugin = "io.github.gradle-nexus.publish-plugin")
    extensions.configure<NexusPublishExtension> {
        repositories {
            sonatype {
                nexusUrl.set(uri("https://oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://oss.sonatype.org/content/repositories/snapshots/"))

                username.set(nexusUsername)
                password.set(nexusPassword)
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