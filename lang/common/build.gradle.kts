plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":lang:api"))

    implementation(project(":base"))
}