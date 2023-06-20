plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":lang:api"))

    implementation(project(":base:impl"))
}