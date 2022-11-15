plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":core:graph:api"))

    implementation(project(":base"))
    implementation(project(":validation:format"))
}