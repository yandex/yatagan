plugins {
    id("yatagan.artifact")
}

kotlin {
    sourceSets.configureEach {
        languageSettings {
            optIn("com.google.devtools.ksp.KspExperimental")
        }
    }
}

dependencies {
    api(project(":lang:api"))
    api(project(":lang:compiled"))

    implementation(project(":base"))
    implementation(libs.ksp.api)
}