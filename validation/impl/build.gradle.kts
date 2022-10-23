plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":validation:api"))
    api(project(":validation:spi"))

    implementation(project(":base"))
    implementation(project(":validation:format"))

    implementation(kotlin("stdlib"))
}