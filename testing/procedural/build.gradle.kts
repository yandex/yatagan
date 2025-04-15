plugins {
    id("yatagan.test-only-module")
    application
}

dependencies {
    api(project(":testing:source-set"))

    implementation(libs.poets.kotlin)
}
