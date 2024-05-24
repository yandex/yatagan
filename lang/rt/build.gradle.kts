plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":lang:common"))

    implementation(project(":api:public"))
    implementation(project(":base:impl"))

    // optional dependency, detected at runtime for compat mode
    compileOnly(libs.testing.dagger.api)
}