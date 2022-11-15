plugins {
    id("yatagan.base-module")
    application
}

dependencies {
    api(project(":testing:source-set"))

    implementation(libs.poets.kotlin)
}
