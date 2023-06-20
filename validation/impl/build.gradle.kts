plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":validation:api"))
    api(project(":validation:spi"))

    implementation(project(":base:impl"))
    implementation(project(":validation:format"))
}