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

    // KSP internals, sometimes required for workarounds
    // FIXME: Remove this once there are no workarounds with access to internals employed.
    implementation(libs.ksp.impl)
    compileOnly(kotlin("compiler-embeddable"))
}