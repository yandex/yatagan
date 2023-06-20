plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":core:graph:api"))

    implementation(project(":base:impl"))
    implementation(project(":validation:format"))
}