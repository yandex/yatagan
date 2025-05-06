plugins {
    id("yatagan.base-module")
}

val yataganVersion: String by extra

val isUnderTeamcity = providers.environmentVariable("TEAMCITY_VERSION").isPresent

tasks {
    if (isUnderTeamcity) {
        register("publish") {
            doLast {
                println("##teamcity[buildStatus text='${yataganVersion}: {build.status.text}']")
            }
        }
    }
}