plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":validation:api"))
    api(project(":validation:spi"))

    implementation(project(":base"))
    implementation(project(":validation:format"))
}